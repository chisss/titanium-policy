package com.titanium.policy.application.orchestration.issuance.orchestrator;

import java.util.List;
import java.util.UUID;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import com.titanium.metadata.enums.product.ProductEnum.IssuanceMode;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.application.exception.IssuanceOrchestrationException;
import com.titanium.policy.application.orchestration.issuance.InsuranceLinePremiumConfirmationService;
import com.titanium.policy.application.orchestration.issuance.InsuranceLinePremiumConfirmationService.ConfirmationSummary;
import com.titanium.policy.application.orchestration.issuance.assembler.InsuranceLineAssembler;
import com.titanium.policy.application.orchestration.issuance.assembler.PolicyProductAssembler;
import com.titanium.policy.application.orchestration.issuance.assembler.ProposalLineAssembler;
import com.titanium.policy.application.orchestration.issuance.executor.RiskAssessmentExecutor;
import com.titanium.policy.command.ActivatePolicyCommand;
import com.titanium.policy.command.CreateInsuranceDirectlyCommand;
import com.titanium.policy.command.CreatePolicyDirectlyCommand;
import com.titanium.policy.command.CreateProposalCommand;
import com.titanium.policy.command.SubmitProposalCommand;
import com.titanium.policy.command.SubmitUnderwritingCommand;
import com.titanium.policy.common.enums.RiskAssessmentStep;
import com.titanium.policy.entity.insurance.InsuranceLine;
import com.titanium.policy.entity.policy.PolicyProduct;
import com.titanium.policy.entity.proposal.ProposalLine;
import com.titanium.policy.generator.PolicyNoGenerator;
import com.titanium.policy.port.ProductServicePort;
import com.titanium.policy.valueobject.IssuancePlanLine;
import com.titanium.policy.valueobject.IssuanceProcessConfig;
import com.titanium.policy.valueobject.IssuanceRequest;
import com.titanium.policy.valueobject.IssuanceResult;
import com.titanium.policy.valueobject.policy.ChannelInfo;
import com.titanium.policy.valueobject.policy.CollectionInfo;
import com.titanium.policy.valueobject.policy.CollectionResult;
import com.titanium.policy.valueobject.policy.PolicyPeriod;
import com.titanium.policy.valueobject.product.ProductIssueRules;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 出单编排器（同步命令式编排）
 * <p>
 * 依产品配置的出单模式（一步/两步/三步）编排出单流程的<b>起点</b>：
 * </p>
 * <ul>
 *   <li><b>一步出单</b>：单聚合、同步、即时返回——直接创建正式保单（含完整险种段与条款责任快照），
 *       编排在此完结；</li>
 *   <li><b>两步/三步出单</b>：跨聚合、含核保跨服务、长周期——本编排器只发<b>起点命令</b>
 *       （创建投保单 / 意向单），随后的「核保 → 承保 → 出单」全程由 {@code IssuanceSaga}
 *       事件驱动接力。</li>
 * </ul>
 * <p>
 * 🔴 <b>本次改造要点</b>：
 * </p>
 * <ol>
 *   <li><b>险种段贯通</b>：三个分支均传递结构化险种段（经 {@link InsuranceLineAssembler} /
 *       {@link PolicyProductAssembler} 装配），取代改造前的 {@code List.of(productCode)} 裸编码；</li>
 *   <li><b>参与方不再丢弃</b>：一步出单此前向命令传 null 参与方，现完整透传；</li>
 *   <li><b>保额与缴费贯通</b>：改造前两步出单传 null 的 {@code sumInsured}/{@code paymentMode}
 *       使 Saga 的真实保费计算被静默跳过，现由段承载真实值；</li>
 *   <li><b>收费与渠道落地</b>：收费方式与渠道信息随命令进入保单。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IssuanceOrchestrator {

    private final CommandGateway         commandGateway;
    private final PolicyNoGenerator      policyNoGenerator;
    private final RiskAssessmentExecutor riskAssessmentExecutor;
    private final ProductServicePort     productServicePort;
    private final InsuranceLineAssembler insuranceLineAssembler;
    private final PolicyProductAssembler policyProductAssembler;
    private final ProposalLineAssembler  proposalLineAssembler;
    private final InsuranceLinePremiumConfirmationService premiumConfirmationService;
    private final PremiumCollectionOrchestrator premiumCollectionOrchestrator;
    private final PremiumScheduleOrchestrator premiumScheduleOrchestrator;

    /**
     * 产品驱动出单编排：由产品域配置决定出单模式（一步/两步/三步），取代调用方硬编码。
     *
     * @param request 出单请求（含结构化险种段方案）
     * @return 出单结果
     */
    public IssuanceResult orchestrate(IssuanceRequest request) {
        String mainProductId = request.mainProductId();
        if (mainProductId == null) {
            return IssuanceResult.rejected(request.bizNo(), "ISSUANCE_MAIN_PRODUCT_MISSING",
                    "出单方案缺少主险段（须含且仅含一个 productCategory=MAIN 的方案行）");
        }
        IssuanceMode mode = productServicePort.getIssuanceMode(mainProductId, request.tenantId());
        log.info("产品驱动出单: bizNo={}, 主险产品={}, 由产品配置决定出单模式={}", request.bizNo(), mainProductId,
                mode);
        IssuanceProcessConfig config = IssuanceProcessConfig.forMode(mode, mainProductId);
        return orchestrate(config, request);
    }

    /**
     * 编排出单流程
     *
     * @param config  出单配置
     * @param request 出单请求
     * @return 出单结果
     */
    public IssuanceResult orchestrate(IssuanceProcessConfig config, IssuanceRequest request) {
        log.info("开始出单编排: bizNo={}, 模式={}, 险种段数={}", request.bizNo(), config.issuanceMode(),
                request.planLines() != null ? request.planLines().size() : 0);

        if (request.isSplitStrategy()) {
            return IssuanceResult.rejected(request.bizNo(), "ISSUANCE_STRATEGY_UNSUPPORTED",
                    "拆分出单（SPLIT_MULTI_POLICY）暂未支持，需营销域商品包能力配合；请用 MERGE_ONE_POLICY");
        }

        // 执行风控校验步骤
        for (RiskAssessmentStep step : config.riskAssessmentSteps()) {
            if (!riskAssessmentExecutor.execute(step, request)) {
                log.warn("风控校验不通过: bizNo={}, 步骤={}", request.bizNo(), step.getCode());
                return IssuanceResult.rejected(request.bizNo(), "ISSUANCE_RISK_REJECTED",
                        step.getName() + "不通过");
            }
        }

        return switch (config.issuanceMode()) {
            case ONE_STEP -> executeOneStep(request);
            case TWO_STEP -> executeTwoStep(request);
            case THREE_STEP -> executeThreeStep(request);
            case CUSTOM -> IssuanceResult.rejected(request.bizNo(), "ISSUANCE_MODE_UNSUPPORTED",
                    "自定义出单模式(CUSTOM)暂未支持，需规则引擎域配合");
        };
    }

    /**
     * 一步出单：直接创建正式保单（免核保短险，如交强险、短期意外险）。
     * <p>
     * 装配完整险种段（含条款与责任快照），使一步出单产出的保单与两步/三步出单结构一致——
     * 下游读侧与理赔无需区分出单模式。
     * </p>
     */
    private IssuanceResult executeOneStep(IssuanceRequest request) {
        String policyId = UUID.randomUUID().toString();
        String policyNo = policyNoGenerator.generatePolicyNo(request.tenantId());
        ConfirmationSummary confirmation = premiumConfirmationService.confirm(
                insuranceLineAssembler.assemble(request), request.insuredPartyList(), request.bizNo(),
                request.bizNo(), request.periodStart(), request.tenantId(), request.channelId(), 1, false);
        List<PolicyProduct> lines = policyProductAssembler.assembleFromInsuranceLines(
                confirmation.lines(), request.tenantId(), confirmation.calculationReferences());
        Money totalPremium = confirmation.totalPremium();

        CreatePolicyDirectlyCommand command = new CreatePolicyDirectlyCommand(policyId, policyNo, request.bizNo(),
                request.marketPackageId(), resolvePolicyForm(request), request.mainProductId(),
                request.insuredPartyList(), lines, request.mainSumInsured(), totalPremium, buildPolicyPeriod(request),
                null, CollectionInfo.initial(request.collectionMode(), totalPremium, java.time.LocalDateTime.now()),
                buildChannelInfo(request), request.insuranceType(), request.tenantId());

        commandGateway.sendAndWait(command);
        IssuanceResult result = IssuanceResult.policyIssued(request.bizNo(),
                new IssuanceResult.IssuedPolicy(policyId, policyNo, "NOT_EFFECTIVE", lines.size(), totalPremium),
                totalPremium);
        try {
            IssuancePlanLine mainPlan = request.mainLine();
            CollectionResult collection = premiumCollectionOrchestrator.collect(policyId,
                    request.holderCustomerId(), totalPremium, request.collectionMode(),
                    request.periodStart() != null ? request.periodStart().toLocalDate() : null, request.tenantId(),
                    confirmation.calculationReferences());
            result = result.withCollection(collection);
            premiumScheduleOrchestrator.generate(policyId, collection.billId(), collection.billingAccountId(),
                    mainPlan != null && mainPlan.paymentFrequency() != null
                            ? mainPlan.paymentFrequency().getCode() : null,
                    mainPlan != null && mainPlan.premiumPaymentYears() != null
                            ? mainPlan.premiumPaymentYears() : 0,
                    totalPremium, request.periodStart() != null ? request.periodStart().toLocalDate() : null,
                    request.tenantId());
            if (collection.allowsActivation()) {
                activateIfPeriodStarted(policyId, request.tenantId());
            }
            log.info("一步出单收费完成: bizNo={}, policyId={}, status={}, billId={}, paymentOrderId={}",
                    request.bizNo(), policyId, collection.status(), collection.billId(), collection.paymentOrderId());
        } catch (RuntimeException exception) {
            // 承保事实已经成立，收费失败不销毁保单；保单保持未生效，后续由补偿流程重试收费。
            log.error("一步出单收费失败（保单保留为未生效）: bizNo={}, policyId={}", request.bizNo(), policyId,
                    exception);
        }
        log.info("一步出单完成: bizNo={}, policyId={}, policyNo={}, 险种段数={}", request.bizNo(), policyId,
                policyNo, lines.size());
        return result;
    }

    /**
     * 两步出单起点：创建投保单。
     * <p>
     * 只发起点命令；投保单创建产生 {@code InsuranceCreatedEvent} 后，「核保 → 承保 → 出单」
     * 全程由 {@code IssuanceSaga} 事件驱动接力。
     * </p>
     */
    private IssuanceResult executeTwoStep(IssuanceRequest request) {
        String insuranceId = UUID.randomUUID().toString();
        String insuranceNo = policyNoGenerator.generateInsuranceNo(request.tenantId());
        List<InsuranceLine> lines = insuranceLineAssembler.assemble(request);
        IssuancePlanLine mainPlan = request.mainLine();

        CreateInsuranceDirectlyCommand command = new CreateInsuranceDirectlyCommand(insuranceId, insuranceNo,
                resolvePolicyForm(request), request.holderCustomerId(), request.insuredCount(),
                request.quotedPremium() != null ? request.quotedPremium().value() : null, request.periodStart(),
                request.periodEnd(), lines, 0, request.insuredPartyList(), request.insuranceType(),
                request.collectionMode(), buildChannelInfo(request), request.bizNo(), request.marketPackageId(),
                request.tenantId(),
                request.mainSumInsured() != null ? request.mainSumInsured().value() : null,
                mainPlan != null && mainPlan.paymentFrequency() != null ? mainPlan.paymentFrequency().getCode() : null,
                mainPlan != null && mainPlan.premiumPaymentYears() != null ? mainPlan.premiumPaymentYears() : 0);

        commandGateway.sendAndWait(command);
        IssuanceResult partialResult = IssuanceResult.insuranceCreated(request.bizNo(), insuranceId, insuranceNo,
                request.quotedPremium());
        try {
            commandGateway.sendAndWait(new SubmitUnderwritingCommand(insuranceId, request.tenantId()));
        } catch (RuntimeException exception) {
            throw new IssuanceOrchestrationException("投保单已创建但提交核保失败", partialResult, exception);
        }
        log.info("两步出单 - 投保单创建完成: bizNo={}, insuranceId={}, 险种段数={}；后续核保/承保/出单由 IssuanceSaga 接力",
                request.bizNo(), insuranceId, lines.size());

        return IssuanceResult.insuranceCreated(request.bizNo(), insuranceId, insuranceNo, request.quotedPremium());
    }

    /**
     * 三步出单起点：创建意向单。
     * <p>
     * 意向段（轻量：意向产品 + 意向保额）随命令落地；意向单提交后由 {@code ProposalIssuanceSaga}
     * 自动转投保单，再由 {@code IssuanceSaga} 接力核保与承保。
     * </p>
     */
    private IssuanceResult executeThreeStep(IssuanceRequest request) {
        String proposalId = UUID.randomUUID().toString();
        String proposalNo = policyNoGenerator.generateProposalNo(request.tenantId());
        IssuancePlanLine mainPlan = request.mainLine();
        List<ProposalLine> proposalLines = proposalLineAssembler.assemble(request);
        ProposalLine mainProposalLine = proposalLines.stream()
                .filter(ProposalLine::isMain)
                .findFirst()
                .orElse(null);

        CreateProposalCommand command = CreateProposalCommand.builder()
                .proposalId(proposalId)
                .proposalNo(proposalNo)
                .policyForm(resolvePolicyForm(request))
                .channel(request.salesChannel())
                .customerId(request.holderCustomerId())
                .intendedSumInsured(request.mainSumInsured())
                .intendedPremium(request.quotedPremium())
                .insurancePeriodStart(request.periodStart())
                .insurancePeriodEnd(request.periodEnd())
                .expectedProductCode(mainProposalLine != null ? mainProposalLine.productCode() : null)
                .proposalLines(proposalLines)
                .insuranceType(ProposalLine.resolveInsuranceType(request.insuranceType(), proposalLines))
                .bizNo(request.bizNo())
                .marketPackageId(request.marketPackageId())
                .tenantId(request.tenantId())
                .insuredPartyList(request.insuredPartyList())
                .collectionMode(request.collectionMode())
                .channelInfo(buildChannelInfo(request))
                .paymentMode(mainPlan != null && mainPlan.paymentFrequency() != null
                        ? mainPlan.paymentFrequency().getCode() : null)
                .premiumPaymentYears(mainPlan != null && mainPlan.premiumPaymentYears() != null
                        ? mainPlan.premiumPaymentYears() : 0)
                .build();

        commandGateway.sendAndWait(command);
        IssuanceResult partialResult = IssuanceResult.proposalCreated(request.bizNo(), proposalId, proposalNo);
        try {
            commandGateway.sendAndWait(new SubmitProposalCommand(proposalId, "统一出单自动提交意向单", request.tenantId()));
        } catch (RuntimeException exception) {
            throw new IssuanceOrchestrationException("意向单已创建但自动提交失败", partialResult, exception);
        }
        log.info("三步出单 - 意向单创建完成: bizNo={}, proposalId={}, proposalNo={}", request.bizNo(), proposalId,
                proposalNo);

        return IssuanceResult.proposalCreated(request.bizNo(), proposalId, proposalNo);
    }

    /**
     * 保单形态：请求未指定时取主险产品配置（产品驱动，不由调用方硬编码）。
     */
    private com.titanium.metadata.enums.policy.PolicyForm resolvePolicyForm(IssuanceRequest request) {
        if (request.policyForm() != null) {
            return request.policyForm();
        }
        ProductIssueRules rules = productServicePort.getIssueRules(request.mainProductId(),
                request.tenantId());
        return rules != null ? rules.policyForm() : null;
    }

    /**
     * 保单期间：等待期与犹豫期取主险产品投保条件配置（此前 policy 域完全未消费该配置）。
     */
    private PolicyPeriod buildPolicyPeriod(IssuanceRequest request) {
        ProductIssueRules rules = productServicePort.getIssueRules(request.mainProductId(),
                request.tenantId());
        return PolicyPeriod.of(request.periodStart(), request.periodEnd(),
                rules != null ? rules.waitingPeriodDays() : 0,
                rules != null ? rules.hesitationPeriodDays() : 0);
    }

    /**
     * 渠道信息（此前命令有 channel 字段但发事件时被丢弃，保单查不到来源渠道）。
     */
    private ChannelInfo buildChannelInfo(IssuanceRequest request) {
        if (request.channelId() == null && request.salesChannel() == null && request.agentId() == null) {
            return null;
        }
        return new ChannelInfo(request.channelId(), null, request.salesChannel(), request.agentId(), null);
    }

    /**
     * 收费条件已满足时尝试生效；保障起期未到时保留未生效状态，由后续定时任务激活。
     */
    private void activateIfPeriodStarted(String policyId, String tenantId) {
        try {
            commandGateway.sendAndWait(new ActivatePolicyCommand(policyId, tenantId));
        } catch (RuntimeException exception) {
            log.info("一步出单保单暂不可生效，等待保障起期: policyId={}, 原因={}", policyId,
                    exception.getMessage());
        }
    }
}
