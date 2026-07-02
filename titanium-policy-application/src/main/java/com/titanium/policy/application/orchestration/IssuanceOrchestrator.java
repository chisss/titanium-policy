package com.titanium.policy.application.orchestration;

import java.util.List;
import java.util.UUID;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import com.titanium.metadata.enums.product.ProductEnum;
import com.titanium.policy.command.CreateInsuranceDirectlyCommand;
import com.titanium.policy.command.CreatePolicyDirectlyCommand;
import com.titanium.policy.command.CreateProposalCommand;
import com.titanium.policy.common.enums.RiskAssessmentStep;
import com.titanium.policy.exception.PolicyBusinessRuleException;
import com.titanium.policy.generator.PolicyNoGenerator;
import com.titanium.policy.port.ProductServicePort;
import com.titanium.policy.valueobject.IssuanceProcessConfig;
import com.titanium.policy.valueobject.IssuanceRequest;
import com.titanium.policy.valueobject.IssuanceResult;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 出单编排器（同步命令式编排）
 * <p>
 * 依产品驱动的出单模式（一步/两步/三步）编排出单流程的<b>起点</b>：
 * </p>
 * <ul>
 *     <li><b>一步出单</b>：单聚合、同步、即时返回——直接创建正式保单，编排在此完结；</li>
 *     <li><b>两步/三步出单</b>：跨聚合、含核保跨服务、长周期——本编排器只发<b>起点命令</b>
 *         （创建投保单 / 意向单），随后的「核保 → 承保 → 出单」全程由 {@code IssuanceSaga}
 *         事件驱动接力，本编排器<b>不再插手中段</b>。</li>
 * </ul>
 * <p>
 * 🔴 单一范式化（方案 A）：出单流程按一致性要求二选一——一步走本同步编排器，两步/三步全程走 Saga。
 * 原先在此直接调用核保服务（{@code underwritingServicePort.createUnderwriting} + Map 拼参）的
 * 「中段核保直调」已删除，核保统一由 Saga 经 {@code UnderwritingDecisionGateway} 处理，
 * 消除「同步编排器起头 + 异步 Saga 接力」控制流被劈成两半、且核保双轨实现的问题。
 * </p>
 */
@Slf4j
@Service
public class IssuanceOrchestrator {

    @Resource
    private CommandGateway         commandGateway;

    @Resource
    private PolicyNoGenerator      policyNoGenerator;

    @Resource
    private RiskAssessmentExecutor riskAssessmentExecutor;

    @Resource
    private ProductServicePort     productServicePort;

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
            case CUSTOM ->
                throw new PolicyBusinessRuleException("ISSUANCE_MODE_UNSUPPORTED", "自定义出单模式(CUSTOM)暂未支持,需规则引擎域配合");
        };
    }

    /**
     * 一步出单：直接创建保单
     */
    private IssuanceResult executeOneStep(IssuanceProcessConfig config, IssuanceRequest request) {
        String policyId = UUID.randomUUID().toString();
        String policyNo = policyNoGenerator.generatePolicyNo();

        CreatePolicyDirectlyCommand command = new CreatePolicyDirectlyCommand(policyId, policyNo, request.productId(),
                request.productCode(), request.policyForm(), request.policyHolderId(), request.insuredCount(),
                request.totalPremium(), request.insurancePeriodStart(), request.insurancePeriodEnd(), request.channel(),
                request.tenantId());

        commandGateway.sendAndWait(command);
        log.info("一步出单完成, policyId={}, policyNo={}", policyId, policyNo);

        return IssuanceResult.success(ProductEnum.IssuanceMode.ONE_STEP, policyId, policyNo, null, null);
    }

    /**
     * 两步出单起点：创建投保单
     * <p>
     * 只发起点命令 {@link CreateInsuranceDirectlyCommand}。投保单创建产生
     * {@code InsuranceCreatedEvent} 后，「核保 → 承保 → 出单」全程由 {@code IssuanceSaga}
     * 事件驱动接力，本编排器不再直接调用核保服务。
     * </p>
     */
    private IssuanceResult executeTwoStep(IssuanceProcessConfig config, IssuanceRequest request) {
        String insuranceId = UUID.randomUUID().toString();
        String insuranceNo = policyNoGenerator.generateInsuranceNo();

        CreateInsuranceDirectlyCommand command = new CreateInsuranceDirectlyCommand(insuranceId, insuranceNo,
                request.policyForm(), request.policyHolderId(), request.insuredCount(),
                request.totalPremium() != null ? request.totalPremium().value() : null, request.insurancePeriodStart(),
                request.insurancePeriodEnd(), List.of(request.productCode()), 0, request.tenantId());

        commandGateway.sendAndWait(command);
        log.info("两步出单 - 投保单创建完成, insuranceId={}, insuranceNo={}；后续核保/承保/出单由 IssuanceSaga 接力",
                insuranceId, insuranceNo);

        return IssuanceResult.success(ProductEnum.IssuanceMode.TWO_STEP, null, null, insuranceId, insuranceNo);
    }

    /**
     * 三步出单起点：创建意向单
     * <p>
     * 只发起点命令 {@link CreateProposalCommand}。意向单提交转投保单后产生
     * {@code InsuranceCreatedEvent}，「核保 → 承保 → 出单」全程由 {@code IssuanceSaga}
     * 事件驱动接力，本编排器不再插手中段。
     * </p>
     */
    private IssuanceResult executeThreeStep(IssuanceProcessConfig config, IssuanceRequest request) {
        String proposalId = UUID.randomUUID().toString();
        String proposalNo = policyNoGenerator.generateProposalNo();

        CreateProposalCommand command = CreateProposalCommand.builder().proposalId(proposalId).proposalNo(proposalNo)
                .policyForm(request.policyForm()).channel(request.channel()).customerId(request.policyHolderId())
                .intendedSumInsured(request.totalPremium()).intendedPremium(request.totalPremium())
                .insurancePeriodStart(request.insurancePeriodStart()).insurancePeriodEnd(request.insurancePeriodEnd())
                .expectedProductCode(request.productCode()).tenantId(request.tenantId()).build();

        commandGateway.sendAndWait(command);
        log.info("三步出单 - 意向单创建完成, proposalId={}, proposalNo={}", proposalId, proposalNo);

        return IssuanceResult.success(ProductEnum.IssuanceMode.THREE_STEP, null, null, null, null)
                .withProposal(proposalId, proposalNo);
    }
}
