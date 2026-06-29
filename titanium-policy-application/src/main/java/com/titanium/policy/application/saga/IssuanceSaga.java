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
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.command.CreatePolicyCommand;
import com.titanium.policy.command.ReceiveUnderwritingResultCommand;
import com.titanium.policy.command.TriggerIssuanceCommand;
import com.titanium.policy.event.insurance.InsuranceCreatedEvent;
import com.titanium.policy.event.insurance.InsuranceIssuedEvent;
import com.titanium.policy.event.insurance.InsuranceSubmittedForUnderwritingEvent;
import com.titanium.policy.event.insurance.UnderwritingResultReceivedEvent;
import com.titanium.policy.service.PolicyNoGenerator;
import com.titanium.policy.service.UnderwritingDecisionGateway;
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

    /** 保单形态 */
    private PolicyForm    policyForm;
    /** 投保人ID */
    private String        holderId;
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
    @SagaEventHandler(associationProperty = "insuranceId")
    public void on(UnderwritingResultReceivedEvent event) {
        ConclusionType resultCode = event.resultCode();
        if (resultCode == ConclusionType.ACCEPT || resultCode == ConclusionType.MODIFY) {
            log.info("[IssuanceSaga] 核保通过，触发承保出单: insuranceId={}", event.insuranceId());
            commandGateway.sendAndWait(new TriggerIssuanceCommand(event.insuranceId(), event.tenantId()));
        } else {
            log.warn("[IssuanceSaga] 核保未通过（{}），结束 Saga: insuranceId={}", resultCode, event.insuranceId());
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
    }
}
