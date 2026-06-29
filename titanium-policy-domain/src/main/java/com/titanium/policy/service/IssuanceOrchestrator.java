package com.titanium.policy.service;

import java.util.List;
import java.util.UUID;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import com.titanium.metadata.enums.product.ProductEnum;
import com.titanium.policy.command.CreateInsuranceDirectlyCommand;
import com.titanium.policy.command.CreatePolicyDirectlyCommand;
import com.titanium.policy.command.CreateProposalCommand;
import com.titanium.policy.exception.PolicyBusinessRuleException;
import com.titanium.policy.valueobject.IssuanceProcessConfig;
import com.titanium.policy.valueobject.RiskAssessmentStep;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 出单编排器
 * <p>
 * 根据出单配置（一步/两步/三步）编排整个出单流程。
 * 负责协调意向单/投保单/保单的创建，以及风控校验步骤的执行。
 * </p>
 */
@Slf4j
@Service
public class IssuanceOrchestrator {

    @Resource
    private CommandGateway     commandGateway;

    @Resource
    private PolicyNoGenerator  policyNoGenerator;

    @Resource
    private RiskAssessmentExecutor riskAssessmentExecutor;

    @Resource
    private ProductServicePort productServicePort;

    @Resource
    private ClauseServicePort clauseServicePort;

    @Resource
    private UnderwritingServicePort underwritingServicePort;

    /**
     * 产品驱动出单编排：由产品域配置决定出单模式（一步/两步/三步），取代调用方硬编码。
     *
     * @param request 出单请求（含 productId/productCode/tenantId）
     * @return 出单结果
     */
    public IssuanceResult orchestrate(IssuanceRequest request) {
        ProductEnum.IssuanceMode mode = productServicePort.getIssuanceMode(request.productId(), request.tenantId());
        log.info("产品驱动出单, 产品={}, 由产品配置决定出单模式={}", request.productCode(), mode);
        IssuanceProcessConfig config = IssuanceProcessConfig.forMode(mode, request.productCode());
        return orchestrate(config, request);
    }

    /**
     * 编排出单流程
     *
     * @param config 出单配置
     * @param request 出单请求
     * @return 出单结果（保单ID 或投保单ID，取决于模式）
     */
    public IssuanceResult orchestrate(IssuanceProcessConfig config, IssuanceRequest request) {
        log.info("开始出单编排, 模式={}, 产品编码={}", config.issuanceMode(), config.productCode());

        // 执行风控校验步骤
        for (RiskAssessmentStep step : config.riskAssessmentSteps()) {
            boolean passed = riskAssessmentExecutor.execute(step, request);
            if (!passed) {
                log.warn("风控校验不通过, 步骤={}", step.getCode());
                return IssuanceResult.rejected(step.getName() + "不通过");
            }
        }

        return switch (config.issuanceMode()) {
            case ONE_STEP -> executeOneStep(config, request);
            case TWO_STEP -> executeTwoStep(config, request);
            case THREE_STEP -> executeThreeStep(config, request);
            case CUSTOM -> throw new PolicyBusinessRuleException("ISSUANCE_MODE_UNSUPPORTED",
                    "自定义出单模式(CUSTOM)暂未支持,需规则引擎域配合");
        };
    }

    /**
     * 一步出单：直接创建保单
     */
    private IssuanceResult executeOneStep(IssuanceProcessConfig config, IssuanceRequest request) {
        String policyId = UUID.randomUUID().toString();
        String policyNo = policyNoGenerator.generatePolicyNo();

        CreatePolicyDirectlyCommand command = new CreatePolicyDirectlyCommand(
                policyId,
                policyNo,
                request.productId(),
                request.productCode(),
                request.policyForm(),
                request.policyHolderId(),
                request.insuredCount(),
                request.totalPremium(),
                request.insurancePeriodStart(),
                request.insurancePeriodEnd(),
                request.channel(),
                request.tenantId()
        );

        commandGateway.sendAndWait(command);
        log.info("一步出单完成, policyId={}, policyNo={}", policyId, policyNo);

        return IssuanceResult.success(ProductEnum.IssuanceMode.ONE_STEP, policyId, policyNo, null, null);
    }

    /**
     * 两步出单：创建投保单 → 核保通过后创建保单
     */
    private IssuanceResult executeTwoStep(IssuanceProcessConfig config, IssuanceRequest request) {
        String insuranceId = UUID.randomUUID().toString();
        String insuranceNo = policyNoGenerator.generateInsuranceNo();

        CreateInsuranceDirectlyCommand command = new CreateInsuranceDirectlyCommand(
                insuranceId,
                insuranceNo,
                request.policyForm(),
                request.policyHolderId(),
                request.insuredCount(),
                request.totalPremium() != null ? request.totalPremium().value() : null,
                request.insurancePeriodStart(),
                request.insurancePeriodEnd(),
                List.of(request.productCode()),
                0,
                request.tenantId()
        );

        commandGateway.sendAndWait(command);
        log.info("两步出单 - 投保单创建完成, insuranceId={}, insuranceNo={}", insuranceId, insuranceNo);

        // 调用核保服务创建核保
        log.info("创建核保申请");
        try {
            // 构建核保请求
            java.util.Map<String, Object> underwritingRequest = new java.util.HashMap<>();
            underwritingRequest.put("insuranceId", insuranceId);
            underwritingRequest.put("policyHolderId", request.policyHolderId());
            underwritingRequest.put("productCode", request.productCode());
            underwritingRequest.put("totalPremium", request.totalPremium());

            // 调用核保服务
            Object underwritingResult = underwritingServicePort.createUnderwriting(underwritingRequest, request.tenantId());
            log.info("核保申请创建成功");
        } catch (Exception e) {
            log.error("创建核保申请失败: {}", e.getMessage());
            return IssuanceResult.rejected("创建核保申请失败: " + e.getMessage());
        }

        return IssuanceResult.success(ProductEnum.IssuanceMode.TWO_STEP, null, null, insuranceId, insuranceNo);
    }

    /**
     * 三步出单：创建意向单 → 提交 → 转投保单 → 核保通过后创建保单
     */
    private IssuanceResult executeThreeStep(IssuanceProcessConfig config, IssuanceRequest request) {
        String proposalId = UUID.randomUUID().toString();
        String proposalNo = policyNoGenerator.generateProposalNo();

        CreateProposalCommand command = CreateProposalCommand.builder()
                .proposalId(proposalId)
                .proposalNo(proposalNo)
                .policyForm(request.policyForm())
                .channel(request.channel())
                .customerId(request.policyHolderId())
                .intendedSumInsured(request.totalPremium())
                .intendedPremium(request.totalPremium())
                .insurancePeriodStart(request.insurancePeriodStart())
                .insurancePeriodEnd(request.insurancePeriodEnd())
                .expectedProductCode(request.productCode())
                .tenantId(request.tenantId())
                .build();

        commandGateway.sendAndWait(command);
        log.info("三步出单 - 意向单创建完成, proposalId={}, proposalNo={}", proposalId, proposalNo);

        return IssuanceResult.success(ProductEnum.IssuanceMode.THREE_STEP, null, null, null, null)
                .withProposal(proposalId, proposalNo);
    }
}
