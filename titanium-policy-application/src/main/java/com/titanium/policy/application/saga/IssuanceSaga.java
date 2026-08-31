package com.titanium.policy.application.saga;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.SagaLifecycle;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.spring.stereotype.Saga;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;

import com.titanium.metadata.enums.billing.PremiumCollectionMode;
import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.application.orchestration.issuance.InsuranceLinePremiumConfirmationService;
import com.titanium.policy.application.orchestration.issuance.InsuranceLinePremiumConfirmationService.ConfirmationSummary;
import com.titanium.policy.application.orchestration.issuance.assembler.PolicyProductAssembler;
import com.titanium.policy.application.orchestration.issuance.orchestrator.PremiumCollectionOrchestrator;
import com.titanium.policy.application.orchestration.issuance.orchestrator.PremiumScheduleOrchestrator;
import com.titanium.policy.command.ActivatePolicyCommand;
import com.titanium.policy.command.CreatePolicyCommand;
import com.titanium.policy.command.LinkInvestmentAccountCommand;
import com.titanium.policy.command.ReceiveUnderwritingResultCommand;
import com.titanium.policy.command.TriggerIssuanceCommand;
import com.titanium.policy.common.constant.PolicyConstants;
import com.titanium.policy.entity.insurance.InsuranceLine;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.entity.insurance.InsuredPartyList.InsuredInfo;
import com.titanium.policy.entity.policy.PolicyProduct;
import com.titanium.policy.event.insurance.InsuranceCreatedEvent;
import com.titanium.policy.event.insurance.InsuranceIssuedEvent;
import com.titanium.policy.event.insurance.InsuranceSubmittedForUnderwritingEvent;
import com.titanium.policy.event.insurance.UnderwritingResultReceivedEvent;
import com.titanium.policy.generator.PolicyNoGenerator;
import com.titanium.policy.port.InvestmentAccountPort;
import com.titanium.policy.port.ProductServicePort;
import com.titanium.policy.port.UnderwritingDecisionGateway;
import com.titanium.policy.service.PolicyIssuanceDomainService;
import com.titanium.policy.valueobject.insurance.UnderwritingDecisionRequest;
import com.titanium.policy.valueobject.insurance.UnderwritingResult;
import com.titanium.policy.valueobject.policy.ChannelInfo;
import com.titanium.policy.valueobject.policy.CollectionInfo;
import com.titanium.policy.valueobject.policy.CollectionResult;
import com.titanium.policy.valueobject.policy.PolicyPeriod;
import com.titanium.policy.valueobject.product.ProductIssueRules;

import lombok.extern.slf4j.Slf4j;

/**
 * 投保出单 Saga（投保 → 核保 → 承保 → 出单 全链路编排）
 * <p>
 * 以 {@code insuranceId} 为关联键，串联投保单生命周期的跨步骤长事务，将原先散落在
 * 应用服务/编排器中的手工串联收敛为单一过程管理器（Process Manager）：
 * </p>
 * <ol>
 *     <li><b>投保</b>：{@link InsuranceCreatedEvent} 启动 Saga，记忆构建保单所需数据；</li>
 *     <li><b>核保</b>：{@link InsuranceSubmittedForUnderwritingEvent} 触发，经 {@link UnderwritingDecisionGateway}
 *         向核保域请求核保结论，并以 {@link ReceiveUnderwritingResultCommand} 将结论回写投保单聚合；</li>
 *     <li><b>承保</b>：{@link UnderwritingResultReceivedEvent} 触发，核保通过则发 {@link TriggerIssuanceCommand}
 *         推进承保出单；核保拒绝/暂缓则结束 Saga（暂缓需人工介入后另行驱动）；</li>
 *     <li><b>出单</b>：{@link InsuranceIssuedEvent} 触发，用记忆的数据发 {@link CreatePolicyCommand}
 *         创建正式保单并结束 Saga。</li>
 * </ol>
 * <p>
 * <b>核保通信</b>：当前经 {@link UnderwritingDecisionGateway} 同步实现对接核保域（注册中心/消息总线就绪前的过渡方案）。
 * 后续可替换为异步消息实现，本 Saga 的编排骨架（事件 → 命令的状态机推进）无需改动。
 * </p>
 */
@Slf4j
@Saga
@JsonAutoDetect(fieldVisibility = Visibility.ANY, getterVisibility = Visibility.NONE,
        isGetterVisibility = Visibility.NONE)
public class IssuanceSaga {

    @Autowired
    private transient CommandGateway commandGateway;

    @Autowired
    private transient PolicyNoGenerator policyNoGenerator;

    @Autowired
    private transient UnderwritingDecisionGateway underwritingDecisionGateway;

    @Autowired
    private transient PolicyIssuanceDomainService policyIssuanceDomainService;

    @Autowired
    private transient InvestmentAccountPort investmentAccountPort;

    @Autowired
    private transient PolicyProductAssembler policyProductAssembler;

    @Autowired
    private transient InsuranceLinePremiumConfirmationService premiumConfirmationService;

    @Autowired
    private transient PremiumCollectionOrchestrator premiumCollectionOrchestrator;

    @Autowired
    private transient PremiumScheduleOrchestrator premiumScheduleOrchestrator;

    @Autowired
    private transient ProductServicePort productServicePort;

    /** 保单形态 */
    private PolicyForm    policyForm;
    /** 投保人ID */
    private String        holderId;
    /** 产品ID（取投保险种编码列表首个，承保载体，透传签发事件供下游采集/分保） */
    private String        productId;
    /** 精确保费（兜底：billing 计算失败时使用） */
    private BigDecimal    exactPremium;
    /** 保障起期 */
    private LocalDateTime insurancePeriodStart;
    /** 保障止期 */
    private LocalDateTime insurancePeriodEnd;
    /** 租户ID */
    private String        tenantId;
    /** 险种三级分类（自投保单事件记忆，出单时透传保单，可空以兼容存量事件） */
    private InsuranceProductType insuranceType;
    /** 参与方清单（自投保单事件记忆，出单时透传保单，含投保人/被保险人/受益人快照） */
    private InsuredPartyList insuredPartyList;
    /** 核保加费率（UW-3：核保结果回传的结构化加费率，出单时并入保费；无加费为 null） */
    private BigDecimal extraPremiumRatio;
    /** 基本保额（BILL-2：供 billing 计算真实保费，null 则回退 exactPremium） */
    private BigDecimal sumInsured;
    /** 缴费模式 code（BILL-2：供 billing 计算） */
    private String paymentMode;
    /** 缴费年数（BILL-2：供 billing 精算计算，0 表示未知） */
    private int premiumPaymentYears;
    /** 投保险种段列表（一单多险载体，承保时精化为保单段并冻结条款责任快照） */
    private List<InsuranceLine> insuranceLines;
    /** 关联意向单ID（三步出单来源，透传保单实现三级贯通） */
    private String proposalId;
    /** 核保单ID（承保依据溯源） */
    private String underwritingId;
    /** 营销包ID（弱引用，透传保单供转化统计） */
    private String marketPackageId;
    /** 出单业务流水号（透传保单供进度回写） */
    private String bizNo;
    /** 收费方式（出单期确定，决定保单生效是否以收讫为前提） */
    private PremiumCollectionMode collectionMode;
    /** 渠道信息（透传保单，此前保单查不到来源渠道） */
    private ChannelInfo channelInfo;
    /** 等待期天数（取产品投保条件配置） */
    private int waitingPeriodDays;
    /** 犹豫期天数（取产品投保条件配置） */
    private int hesitationPeriodDays;

    /**
     * 【投保】投保单创建 → 启动 Saga，记忆后续创建保单所需数据
     */
    @StartSaga
    @SagaEventHandler(associationProperty = "insuranceId")
    public void on(InsuranceCreatedEvent event) {
        log.info("[IssuanceSaga] 启动: insuranceId={}, tenantId={}", event.insuranceId(), event.tenantId());
        this.policyForm = event.policyForm();
        this.holderId = event.holderId();
        // 主险产品ID必须取结构化险种段；产品编码不是产品主键，不能用于远程取数或保单冗余字段。
        InsuranceLine mainLine = event.mainLine();
        this.productId = mainLine != null ? mainLine.productId() : null;
        this.exactPremium = event.exactPremium();
        this.insurancePeriodStart = event.insurancePeriodStart();
        this.insurancePeriodEnd = event.insurancePeriodEnd();
        this.tenantId = event.tenantId();
        this.insuranceType = event.insuranceType();
        // 记忆参与方清单快照，出单时透传正式保单
        this.insuredPartyList = event.insuredPartyList();
        // BILL-2：记忆保额/缴费模式，出单时调 billing 计算真实保费
        this.sumInsured = event.sumInsured();
        this.paymentMode = event.paymentMode();
        this.premiumPaymentYears = event.premiumPaymentYears();
        // 记忆险种段（一单多险载体）与出单期确定的收费/渠道/溯源信息，出单时透传保单
        this.insuranceLines = event.insuranceLines();
        this.proposalId = event.proposalId();
        this.marketPackageId = event.marketPackageId();
        this.bizNo = event.bizNo();
        this.collectionMode = event.collectionMode();
        this.channelInfo = event.channelInfo();
        // 取产品投保条件的等待期/犹豫期配置（此前 policy 域完全未消费该配置）
        rememberPolicyPeriodDays(event.insuranceLines());
    }

    /**
     * 记忆主险产品配置的等待期与犹豫期天数（出单时构造保单期间用）。
     * <p>
     * 远程取数失败不阻断出单，按无等待期/无犹豫期兜底并告警——二者影响理赔与退保判定，
     * 缺失需人工补正。
     * </p>
     */
    private void rememberPolicyPeriodDays(List<InsuranceLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        InsuranceLine main = lines.stream().filter(InsuranceLine::isMain).findFirst().orElse(lines.get(0));
        if (main.productId() == null) {
            return;
        }
        try {
            ProductIssueRules rules = productServicePort.getIssueRules(main.productId(), tenantId);
            if (rules != null) {
                this.waitingPeriodDays = rules.waitingPeriodDays() != null ? rules.waitingPeriodDays() : 0;
                this.hesitationPeriodDays = rules.hesitationPeriodDays() != null ? rules.hesitationPeriodDays() : 0;
            }
        } catch (Exception ex) {
            log.warn("[IssuanceSaga] 取产品等待期/犹豫期配置失败，按无等待期兜底（需人工补正）: productId={}",
                    main.productId(), ex);
        }
    }

    /**
     * 【核保】投保单提交核保 → 请求核保域出具结论，并将结论回写投保单聚合
     */
    @SagaEventHandler(associationProperty = "insuranceId")
    public void on(InsuranceSubmittedForUnderwritingEvent event) {
        log.info("[IssuanceSaga] 提交核保，请求核保结论: insuranceId={}", event.insuranceId());

        // UW-2：从记忆的参与方清单提取首要被保人核保要素（年龄/性别），构建富核保请求。
        // 清单缺失时各要素为 null，核保域按保守标准体兜底。
        InsuredInfo primaryInsured = extractPrimaryInsured();
        UnderwritingDecisionRequest request = new UnderwritingDecisionRequest(event.insuranceId(), event.holderId(),
                event.insuredCount(), this.sumInsured, event.exactPremium(), event.currency(), event.productCodes(),
                event.tenantId(), primaryInsured != null ? primaryInsured.age() : null,
                primaryInsured != null ? primaryInsured.gender() : null,
                null, null);
        UnderwritingResult result = underwritingDecisionGateway.requestDecision(request);

        commandGateway.sendAndWait(new ReceiveUnderwritingResultCommand(event.insuranceId(), result, event.tenantId()));
        log.info("[IssuanceSaga] 核保结论已回写投保单: insuranceId={}, 结论={}, 加费率={}", event.insuranceId(),
                result.resultCode(), result.extraPremiumRatio());
    }

    /**
     * 【承保】核保结果接收 → 核保通过推进承保出单；拒绝/暂缓结束 Saga
     */
    /**
     * 【承保】核保结果接收 → 核保通过推进承保出单；拒绝/暂缓结束 Saga
     * <p>
     * 「哪些核保结论算通过」的<b>承保准入规则</b>委托 {@link PolicyIssuanceDomainService#canIssueByConclusion}
     * 裁决，Saga 本身只依裁决结果做<b>流程路由</b>（通过则发命令推进，否则结束流程），不含业务判断。
     * </p>
     */
    @SagaEventHandler(associationProperty = "insuranceId")
    public void on(UnderwritingResultReceivedEvent event) {
        // UW-3：记忆核保加费率，出单时并入保费
        this.extraPremiumRatio = event.extraPremiumRatio();
        // 记忆核保单ID，出单时透传保单（承保依据溯源，此前保单查不到核保单）
        this.underwritingId = event.underwritingId();
        // 段级结论回写：整单结论下发各段，拒保段保费不计入总保费
        if (this.insuranceLines != null) {
            List<InsuranceLine> updated = new ArrayList<>();
            for (InsuranceLine line : this.insuranceLines) {
                updated.add(line.withUnderwritingResult(event.resultCode(), event.extraPremiumRatio()));
            }
            this.insuranceLines = updated;
        }

        // 以核保事件构造核保结果值对象，交领域服务裁决承保准入（业务规则不落在 Saga）
        UnderwritingResult result = new UnderwritingResult(event.underwritingId(), event.resultCode(), event.opinion(),
                event.underwriterId(), event.underwritingTime(), event.underwritingCondition(),
                event.extraPremiumRatio());

        if (policyIssuanceDomainService.canIssueByConclusion(result)) {
            log.info("[IssuanceSaga] 核保通过，触发承保出单: insuranceId={}", event.insuranceId());
            commandGateway.sendAndWait(new TriggerIssuanceCommand(event.insuranceId(), event.tenantId()));
        } else {
            log.warn("[IssuanceSaga] 核保未通过（{}），结束 Saga: insuranceId={}", event.resultCode(), event.insuranceId());
            SagaLifecycle.end();
        }
    }

    /**
     * 【出单】投保单承保出单 → 创建正式保单，结束 Saga
     */
    @EndSaga
    @SagaEventHandler(associationProperty = "insuranceId")
    public void on(InsuranceIssuedEvent event) {
        String policyId = UUID.randomUUID().toString();
        String policyNo = policyNoGenerator.generatePolicyNo(tenantId);

        String confirmationBizNo = bizNo != null && !bizNo.isBlank() ? bizNo : event.insuranceId();
        ConfirmationSummary confirmation = premiumConfirmationService.confirm(
                insuranceLines, insuredPartyList, event.insuranceId(), confirmationBizNo,
                insurancePeriodStart, tenantId, channelInfo != null ? channelInfo.channelId() : null, 1, true);
        Money standardPremium = confirmation.standardPremium();
        Money payablePremium = confirmation.totalPremium();
        List<PolicyProduct> policyProducts = policyProductAssembler.assembleFromInsuranceLines(
                confirmation.lines(), tenantId, confirmation.calculationReferences());

        CreatePolicyCommand command = new CreatePolicyCommand(
                policyId,
                policyNo,
                event.insuranceId(),
                proposalId,
                underwritingId,
                bizNo,
                marketPackageId,
                policyForm,
                productId,
                null,
                insuredPartyList,
                policyProducts,
                sumInsured != null ? Money.of(sumInsured, payablePremium.currency()) : null,
                standardPremium,
                payablePremium,
                PolicyPeriod.of(insurancePeriodStart, insurancePeriodEnd, waitingPeriodDays, hesitationPeriodDays),
                null,
                CollectionInfo.initial(collectionMode, payablePremium, LocalDateTime.now()),
                channelInfo,
                insuranceType,
                tenantId);

        commandGateway.sendAndWait(command);
        log.info(
                "[IssuanceSaga] 承保出单完成，已创建保单: insuranceId={}, policyId={}, policyNo={}, 标准保费={}, 加费率={}, 最终保费={}",
                event.insuranceId(), policyId, policyNo, standardPremium.value(), extraPremiumRatio,
                payablePremium.value());

        // 出单后收费：按收费方式路由（开账单 → 建支付单 → 收讫回写），取代此前的「只开账单不收钱」。
        // 收费失败不销毁保单——保单停未生效、账单待催缴，可重新发起收款。
        CollectionResult collection = null;
        try {
            collection = premiumCollectionOrchestrator.collect(policyId, holderId, payablePremium,
                    collectionMode, insurancePeriodStart != null ? insurancePeriodStart.toLocalDate() : null,
                    tenantId, confirmation.calculationReferences());
            log.info("[IssuanceSaga] 收费编排完成: policyId={}, 收费方式={}, 收讫状态={}, billId={}, 支付单={}",
                    policyId, collectionMode, collection.status(), collection.billId(),
                    collection.paymentOrderId());
            // 已收讫（免支付）或后付挂账（先享后付）时保单即刻满足保费条件，驱动生效；
            // 其余方式待收费回调经 PremiumCollectionSaga 驱动。
            if (collection.allowsActivation()) {
                activateIfPeriodStarted(policyId);
            }
        } catch (Exception ex) {
            log.error("[IssuanceSaga] 收费编排异常（不阻断出单，保单停未生效待催缴）: policyId={}", policyId, ex);
        }

        // 账单成功后生成期缴计划；失败由编排器隔离，不回滚已创建的保单与账单。
        premiumScheduleOrchestrator.generate(policyId, collection != null ? collection.billId() : null,
                collection != null ? collection.billingAccountId() : null, paymentMode, premiumPaymentYears,
                payablePremium,
                insurancePeriodStart != null ? insurancePeriodStart.toLocalDate() : LocalDateTime.now().toLocalDate(),
                tenantId);

        // 投连/万能保单：出单后经 InvestmentAccountPort 开立投资账户并挂接到保单（账户失败不阻断出单，待补偿）
        if (policyForm != null && policyForm.isInvestmentLinked()) {
            try {
                String accountId = investmentAccountPort.openAccount(policyId, policyForm, payablePremium, tenantId);
                if (accountId != null) {
                    commandGateway.sendAndWait(
                            new LinkInvestmentAccountCommand(policyId, accountId, PolicyConstants.POLICY_SYSTEM,
                                    tenantId));
                    log.info("[IssuanceSaga] 投连/万能保单已挂接投资账户: policyId={}, accountId={}", policyId,
                            accountId);
                } else {
                    log.error("[IssuanceSaga] 开立投资账户返回空（不阻断出单，待补偿）: policyId={}", policyId);
                }
            } catch (Exception ex) {
                log.error("[IssuanceSaga] 开立/挂接投资账户失败（不阻断出单，待补偿）: policyId={}", policyId, ex);
            }
        }
    }

    /**
     * BILL-2：调 billing 计算真实标准保费。
     * <p>
     * 有 sumInsured + productId 时发起远程保费计算；任一缺失或远程失败时回退 exactPremium，
     * 保证出单不因保费计算失败而中断。
     * </p>
     */
    /**
     * 保费条件已满足时驱动保单生效（保障起期未到则由定时任务后续激活）。
     * <p>
     * 生效的两个前提——保费条件与保障起期——均由聚合内守护（{@code CollectionInfo} 与
     * {@code PolicyPeriod}）。此处仅在收费条件满足时尝试激活；起期未到时聚合会拒绝，
     * 属预期路径故仅记日志不视为异常。
     * </p>
     */
    private void activateIfPeriodStarted(String policyId) {
        try {
            commandGateway.sendAndWait(new ActivatePolicyCommand(policyId, tenantId));
            log.info("[IssuanceSaga] 保费条件已满足，保单已生效: policyId={}", policyId);
        } catch (Exception ex) {
            log.info("[IssuanceSaga] 保单暂不可生效（保障起期未到，待定时任务激活）: policyId={}, 原因={}", policyId,
                    ex.getMessage());
        }
    }

    /**
     * 提取首要被保人（参与方清单被保险人列表首个），供富核保请求填充年龄/性别等风险要素。
     *
     * @return 首要被保人信息；清单缺失或无被保人时返回 null
     */
    private InsuredInfo extractPrimaryInsured() {
        if (insuredPartyList == null || insuredPartyList.insuredList() == null
                || insuredPartyList.insuredList().isEmpty()) {
            return null;
        }
        return insuredPartyList.insuredList().get(0);
    }

}
