package com.titanium.policy.service;

import java.util.List;
import java.util.UUID;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import com.titanium.policy.command.ConvertProposalToInsuranceCommand;
import com.titanium.policy.command.CreateInsuranceDirectlyCommand;
import com.titanium.policy.command.CreatePolicyDirectlyCommand;
import com.titanium.policy.command.CreateProposalCommand;
import com.titanium.policy.service.ClauseServicePort;
import com.titanium.policy.service.ProductServicePort;
import com.titanium.policy.service.UnderwritingServicePort;
import com.titanium.policy.valueobject.Amount;
import com.titanium.policy.valueobject.IssuanceMode;
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

        return IssuanceResult.success(IssuanceMode.ONE_STEP, policyId, policyNo, null, null);
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

        return IssuanceResult.success(IssuanceMode.TWO_STEP, null, null, insuranceId, insuranceNo);
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

        return IssuanceResult.success(IssuanceMode.THREE_STEP, null, null, null, null)
                .withProposal(proposalId, proposalNo);
    }
}
