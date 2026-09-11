package com.titanium.policy.infrastructure.messaging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.policy.application.orchestration.payment.PremiumCollectionResultOrchestrator;
import com.titanium.policy.infrastructure.messaging.mapper.PolicyPaymentResultMapperImpl;
import com.titanium.policy.valueobject.payment.PremiumCollectionNotice;

/**
 * 支付成功入站监听器用例（policy 域）
 * <p>
 * 锁死两件事：① 支付成功报文能翻译为保单域实收通知（businessId→policyId、金额+币种→Money）；
 * ② 🔴 必须按 businessType 过滤本域消息 —— {@code payment-order-paid} 是支付域对所有业务域的
 * 统一出口，赔付/退费消息的 businessId 若被当作保单ID 回写，会把赔款记成保费收缴。
 * </p>
 */
class PaymentPaidListenerTest {

    private PremiumCollectionResultOrchestrator orchestrator;
    private PaymentPaidListener listener;

    @BeforeEach
    void setUp() {
        orchestrator = mock(PremiumCollectionResultOrchestrator.class);
        listener = new PaymentPaidListener(orchestrator, new PolicyPaymentResultMapperImpl());
    }

    @Test
    void shouldTranslatePaymentMessageIntoCollectionNotice() {
        listener.onPaymentPaid(payload("POLICY-001", "POLICY", "CNY"));

        ArgumentCaptor<PremiumCollectionNotice> captor = ArgumentCaptor.forClass(PremiumCollectionNotice.class);
        verify(orchestrator).onPaymentSucceeded(captor.capture());
        PremiumCollectionNotice notice = captor.getValue();
        assertEquals("POLICY-001", notice.policyId(), "businessId 是业务单号，本域过滤后即保单ID");
        assertEquals("PAY-001", notice.paymentId());
        assertEquals("PAY-NO-001", notice.paymentNo());
        assertEquals(0, new BigDecimal("8888.00").compareTo(notice.collectedAmount().value()));
        assertEquals("CNY", notice.collectedAmount().currency());
        assertEquals(LocalDateTime.of(2026, 9, 11, 10, 0), notice.collectedAt());
        assertEquals("TENANT-001", notice.tenantId());
    }

    /**
     * 理赔赔付、保全退费同样发布到本主题，businessId 是赔案/保全单号而非保单ID，必须跳过。
     */
    @Test
    void shouldSkipMessageFromOtherBusinessDomain() {
        listener.onPaymentPaid(payload("CLAIM-001", "CLAIM", "CNY"));

        verifyNoInteractions(orchestrator);
    }

    /**
     * 币种字段随本次改动才加入出站载荷，此前滞留主题的在途消息没有该字段，须回落人民币正常回写。
     */
    @Test
    void shouldFallBackToCnyWhenCurrencyMissing() {
        listener.onPaymentPaid(payloadWithoutCurrency("POLICY-002"));

        ArgumentCaptor<PremiumCollectionNotice> captor = ArgumentCaptor.forClass(PremiumCollectionNotice.class);
        verify(orchestrator).onPaymentSucceeded(captor.capture());
        assertEquals("CNY", captor.getValue().collectedAmount().currency());
    }

    @Test
    void shouldIgnorePayloadMissingRequiredFields() {
        assertDoesNotThrow(() -> listener.onPaymentPaid("{\"businessType\":\"POLICY\",\"paidAt\":\"2026-09-11T10:00:00\"}"));

        verifyNoInteractions(orchestrator);
    }

    @Test
    void shouldNotThrowWhenPayloadIsMalformed() {
        assertDoesNotThrow(() -> listener.onPaymentPaid("{not-a-json"));

        verifyNoInteractions(orchestrator);
    }

    /**
     * 重复投递（实收已记账）与保单不存在等场景重试不可能成功，重抛只会让消息被无限重放。
     */
    @Test
    void shouldSwallowOrchestratorFailure() {
        doThrow(new IllegalStateException("支付流水 PAY-001 已记账，忽略重复回调"))
                .when(orchestrator).onPaymentSucceeded(any(PremiumCollectionNotice.class));

        assertDoesNotThrow(() -> listener.onPaymentPaid(payload("POLICY-003", "POLICY", "CNY")));
    }

    /**
     * payment 域 {@code PaymentProcessedEvent} 经 fastjson2 序列化后的载荷形态（字段名与对端一致）。
     */
    private String payload(String businessId, String businessType, String currency) {
        return """
                {"paymentId":"PAY-001","thirdPartyTradeNo":"THIRD-001","status":"SUCCESS",
                 "paidAt":"2026-09-11T10:00:00","processedBy":"tester","tenantId":"TENANT-001",
                 "paymentNo":"PAY-NO-001","businessId":"%s","businessType":"%s",
                 "amount":8888.00,"currency":"%s"}
                """.formatted(businessId, businessType, currency);
    }

    /**
     * 本字段（currency）落地前发布的历史报文形态：无币种字段。
     */
    private String payloadWithoutCurrency(String businessId) {
        return """
                {"paymentId":"PAY-002","status":"SUCCESS","paidAt":"2026-09-11T10:00:00",
                 "tenantId":"TENANT-001","paymentNo":"PAY-NO-002","businessId":"%s",
                 "businessType":"POLICY","amount":8888.00}
                """.formatted(businessId);
    }
}
