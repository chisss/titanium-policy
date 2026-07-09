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

import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.command.CreatePolicyCommand;
import com.titanium.policy.command.LinkInvestmentAccountCommand;
import com.titanium.policy.command.ReceiveUnderwritingResultCommand;
import com.titanium.policy.command.TriggerIssuanceCommand;
import com.titanium.policy.common.constant.PolicyConstants;
import com.titanium.policy.event.insurance.InsuranceCreatedEvent;
import com.titanium.policy.event.insurance.InsuranceIssuedEvent;
import com.titanium.policy.event.insurance.InsuranceSubmittedForUnderwritingEvent;
import com.titanium.policy.event.insurance.UnderwritingResultReceivedEvent;
import com.titanium.policy.generator.PolicyNoGenerator;
import com.titanium.policy.port.BillingServicePort;
import com.titanium.policy.port.InvestmentAccountPort;
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

    /** 保单形态 */
    private PolicyForm    policyForm;
    /** 投保人ID */
    private String        holderId;
    /** 产品ID（取投保险种编码列表首个，承保载体，透传签发事件供下游采集/分保） */
    private String        productId;
    /** 精确保费 */
    private BigDecimal    exactPremium;
    /** 保障起期 */
    private LocalDateTime insurancePeriodStart;
    /** 保障止期 */
    private LocalDateTime insurancePeriodEnd;
    /** 租户ID */
    private String        tenantId;

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
    }

    /**
     * 【核保】投保单提交核保 → 请求核保域出具结论，并将结论回写投保单聚合
     */
    @SagaEventHandler(associationProperty = "insuranceId")
    public void on(InsuranceSubmittedForUnderwritingEvent event) {
        log.info("[IssuanceSaga] 提交核保，请求核保结论: insuranceId={}", event.insuranceId());

        UnderwritingDecisionRequest request = new UnderwritingDecisionRequest(event.insuranceId(), event.holderId(),
                event.insuredCount(), event.exactPremium(), event.currency(), event.productCodes(), event.tenantId());
        UnderwritingResult result = underwritingDecisionGateway.requestDecision(request);

        commandGateway.sendAndWait(new ReceiveUnderwritingResultCommand(event.insuranceId(), result, event.tenantId()));
        log.info("[IssuanceSaga] 核保结论已回写投保单: insuranceId={}, 结论={}", event.insuranceId(),
                result.resultCode());
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
        // 以核保事件构造核保结果值对象，交领域服务裁决承保准入（业务规则不落在 Saga）
        UnderwritingResult result = new UnderwritingResult(event.underwritingId(), event.resultCode(), event.opinion(),
                event.underwriterId(), event.underwritingTime(), event.underwritingCondition());

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
        Money premium = exactPremium != null ? Money.of(exactPremium, "CNY") : Money.zero("CNY");

        CreatePolicyCommand command = new CreatePolicyCommand(
                policyId,
                policyNo,
                event.insuranceId(),
                policyForm,
                productId,
                null,
                holderId,
                null,
                premium,
                premium,
                insurancePeriodStart,
                insurancePeriodEnd,
                null,
                tenantId);

        commandGateway.sendAndWait(command);
        log.info("[IssuanceSaga] 承保出单完成，已创建保单: insuranceId={}, policyId={}, policyNo={}",
                event.insuranceId(), policyId, policyNo);

        // 出单后触发计费：经 BillingServicePort 为保单开立首期保费账单（跨服务同步，账单失败不回滚保单）
        try {
            BillingResult billingResult = billingServicePort.createPremiumBill(
                    new PremiumBillRequest(policyId, holderId, premium, null, tenantId));
            log.info("[IssuanceSaga] 首期保费账单开立结果: policyId={}, success={}, billId={}", policyId,
                    billingResult.success(), billingResult.billId());
        } catch (Exception ex) {
            log.error("[IssuanceSaga] 开立首期保费账单失败（不阻断出单，待补偿）: policyId={}", policyId, ex);
        }

        // 投连/万能保单：出单后经 InvestmentAccountPort 开立投资账户并挂接到保单（账户失败不阻断出单，待补偿）
        if (policyForm != null && policyForm.isInvestmentLinked()) {
            try {
                String accountId = investmentAccountPort.openAccount(policyId, policyForm, premium, tenantId);
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
}
