package com.titanium.policy.application.saga;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.SagaLifecycle;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.spring.stereotype.Saga;
import org.springframework.beans.factory.annotation.Autowired;

import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.command.CreatePolicyCommand;
import com.titanium.policy.command.LinkInvestmentAccountCommand;
import com.titanium.policy.command.ReceiveUnderwritingResultCommand;
import com.titanium.policy.command.TriggerIssuanceCommand;
import com.titanium.policy.common.constant.PolicyConstants;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.entity.insurance.InsuredPartyList.InsuredInfo;
import com.titanium.policy.event.insurance.InsuranceCreatedEvent;
import com.titanium.policy.event.insurance.InsuranceIssuedEvent;
import com.titanium.policy.event.insurance.InsuranceSubmittedForUnderwritingEvent;
import com.titanium.policy.event.insurance.UnderwritingResultReceivedEvent;
import com.titanium.policy.generator.PolicyNoGenerator;
import com.titanium.policy.port.BillingServicePort;
import com.titanium.policy.port.InvestmentAccountPort;
import com.titanium.policy.port.PremiumCalculationGateway;
import com.titanium.policy.port.UnderwritingDecisionGateway;
import com.titanium.policy.service.PolicyIssuanceDomainService;
import com.titanium.policy.valueobject.billing.BillingResult;
import com.titanium.policy.valueobject.billing.PremiumBillRequest;
import com.titanium.policy.valueobject.insurance.UnderwritingDecisionRequest;
import com.titanium.policy.valueobject.insurance.UnderwritingResult;

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
    private transient BillingServicePort billingServicePort;

    @Autowired
    private transient InvestmentAccountPort investmentAccountPort;

    @Autowired
    private transient PremiumCalculationGateway premiumCalculationGateway;

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

    /**
     * 【投保】投保单创建 → 启动 Saga，记忆后续创建保单所需数据
     */
    @StartSaga
    @SagaEventHandler(associationProperty = "insuranceId")
    public void on(InsuranceCreatedEvent event) {
        log.info("[IssuanceSaga] 启动: insuranceId={}, tenantId={}", event.insuranceId(), event.tenantId());
        this.policyForm = event.policyForm();
        this.holderId = event.holderId();
        // 取投保险种编码列表首个作为产品ID（承保载体），供出单事件透传下游
        this.productId = event.productCodes() != null && !event.productCodes().isEmpty()
                ? event.productCodes().get(0) : null;
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
                event.insuredCount(), event.exactPremium(), event.currency(), event.productCodes(), event.tenantId(),
                primaryInsured != null ? primaryInsured.age() : null,
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
        String policyNo = policyNoGenerator.generatePolicyNo();

        // BILL-2：调 billing 计算真实标准保费；失败时回退 exactPremium 不阻断出单
        Money basePremium = calculateBasePremium();

        // UW-3：核保加费并入保费——最终应付保费 = 标准保费 ×(1 + 加费率)；无加费时等于标准保费
        Money finalPremium = applyExtraPremium(basePremium);

        CreatePolicyCommand command = new CreatePolicyCommand(
                policyId,
                policyNo,
                event.insuranceId(),
                policyForm,
                productId,
                null,
                holderId,
                null,
                insuredPartyList,
                basePremium,
                finalPremium,
                insurancePeriodStart,
                insurancePeriodEnd,
                null,
                insuranceType,
                tenantId);

        commandGateway.sendAndWait(command);
        log.info(
                "[IssuanceSaga] 承保出单完成，已创建保单: insuranceId={}, policyId={}, policyNo={}, 标准保费={}, 加费率={}, 最终保费={}",
                event.insuranceId(), policyId, policyNo, basePremium.value(), extraPremiumRatio,
                finalPremium.value());

        // 出单后触发计费：经 BillingServicePort 为保单开立首期保费账单（跨服务同步，账单失败不回滚保单）
        try {
            BillingResult billingResult = billingServicePort.createPremiumBill(
                    new PremiumBillRequest(policyId, holderId, finalPremium, null, tenantId));
            log.info("[IssuanceSaga] 首期保费账单开立结果: policyId={}, success={}, billId={}", policyId,
                    billingResult.success(), billingResult.billId());
        } catch (Exception ex) {
            log.error("[IssuanceSaga] 开立首期保费账单失败（不阻断出单，待补偿）: policyId={}", policyId, ex);
        }

        // BILL-3：出单后生成期缴计划（寿险期缴保费，跨服务同步，失败不阻断出单）
        if (paymentMode != null && premiumPaymentYears > 0) {
            try {
                int totalPeriods = calculateTotalPeriods(paymentMode, premiumPaymentYears);
                java.math.BigDecimal installmentAmount = calculateInstallmentAmount(finalPremium, paymentMode,
                        premiumPaymentYears);
                java.time.LocalDate firstDueDate = insurancePeriodStart != null
                        ? insurancePeriodStart.toLocalDate()
                        : java.time.LocalDate.now();

                com.titanium.policy.valueobject.billing.PremiumScheduleRequest scheduleRequest =
                        new com.titanium.policy.valueobject.billing.PremiumScheduleRequest(policyId, paymentMode,
                                totalPeriods, installmentAmount, finalPremium.currency(), firstDueDate, tenantId);
                billingServicePort.generatePremiumSchedule(scheduleRequest);
                log.info("[IssuanceSaga] 期缴计划生成完成: policyId={}, mode={}, periods={}, installment={}",
                        policyId, paymentMode, totalPeriods, installmentAmount);
            } catch (Exception ex) {
                log.error("[IssuanceSaga] 生成期缴计划失败（不阻断出单，待补偿）: policyId={}", policyId, ex);
            }
        }

        // 投连/万能保单：出单后经 InvestmentAccountPort 开立投资账户并挂接到保单（账户失败不阻断出单，待补偿）
        if (policyForm != null && policyForm.isInvestmentLinked()) {
            try {
                String accountId = investmentAccountPort.openAccount(policyId, policyForm, finalPremium, tenantId);
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
    private Money calculateBasePremium() {
        if (sumInsured != null && productId != null) {
            try {
                // 从参与方清单提取首要被保人要素（年龄/性别）作为 subjectData
                java.util.Map<String, Object> subjectData = new java.util.HashMap<>();
                InsuredInfo primary = extractPrimaryInsured();
                if (primary != null) {
                    subjectData.put("age", primary.age());
                    if (primary.gender() != null) {
                        subjectData.put("gender", primary.gender().name());
                    }
                }
                // 推导保障期（年）：从保障起止日期计算
                int coverageYears = 0;
                if (insurancePeriodStart != null && insurancePeriodEnd != null) {
                    coverageYears = (int) java.time.temporal.ChronoUnit.YEARS.between(
                            insurancePeriodStart.toLocalDate(), insurancePeriodEnd.toLocalDate());
                }
                int totalPeriods = premiumPaymentYears > 0 ? premiumPaymentYears : (coverageYears > 0 ? coverageYears : 1);
                String mode = paymentMode != null ? paymentMode : "ANNUAL";
                PremiumCalculationGateway.StandardPremiumRequest request =
                        new PremiumCalculationGateway.StandardPremiumRequest(
                                productId, sumInsured, "CNY", mode, totalPeriods,
                                coverageYears, subjectData, tenantId);
                PremiumCalculationGateway.StandardPremiumResult result =
                        premiumCalculationGateway.calculatePremium(request);
                log.info("[IssuanceSaga] billing 真实保费计算成功: productId={}, 总保费={}",
                        productId, result.totalPremium());
                return Money.of(result.totalPremium(), result.currency() != null ? result.currency() : "CNY");
            } catch (Exception ex) {
                log.warn("[IssuanceSaga] billing 保费计算失败，回退 exactPremium（不阻断出单）: productId={}", productId, ex);
            }
        }
        // 回退：使用投保时透传的 exactPremium
        return exactPremium != null ? Money.of(exactPremium, "CNY") : Money.zero("CNY");
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

    /**
     * 核保加费并入保费（UW-3）：最终应付保费 = 标准保费 ×(1 + 加费率)。
     * <p>
     * 加费率来自核保域回传（{@link UnderwritingResult#extraPremiumRatio()}），语义与核保域
     * {@code ExtraPremium.applyTo} 一致。无加费（率为 null 或非正）时原样返回标准保费。
     * </p>
     *
     * @param standardPremium 标准保费
     * @return 加费后应付保费
     */
    private Money applyExtraPremium(Money standardPremium) {
        if (extraPremiumRatio == null || extraPremiumRatio.compareTo(BigDecimal.ZERO) <= 0) {
            return standardPremium;
        }
        return standardPremium.multiply(BigDecimal.ONE.add(extraPremiumRatio));
    }

    /**
     * 计算总期数（BILL-3）：根据缴费模式与缴费年数推算期缴计划总期数
     *
     * @param mode 缴费模式（LUMP_SUM/ANNUAL/MONTHLY）
     * @param years 缴费年数
     * @return 总期数
     */
    private int calculateTotalPeriods(String mode, int years) {
        if ("LUMP_SUM".equals(mode)) {
            return 1;
        } else if ("MONTHLY".equals(mode)) {
            return years * 12;
        } else { // ANNUAL
            return years;
        }
    }

    /**
     * 计算每期应缴金额（BILL-3）：总保费 ÷ 总期数
     *
     * @param totalPremium 总保费
     * @param mode 缴费模式
     * @param years 缴费年数
     * @return 每期应缴金额
     */
    private java.math.BigDecimal calculateInstallmentAmount(Money totalPremium, String mode, int years) {
        int periods = calculateTotalPeriods(mode, years);
        return totalPremium.value().divide(java.math.BigDecimal.valueOf(periods), 2,
                java.math.RoundingMode.HALF_UP);
    }
}
