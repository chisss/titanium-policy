package com.titanium.policy.infrastructure.messaging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

/**
 * 保费收取失败入站监听器用例（policy 域）
 * <p>
 * 本监听器**有意不改动任何状态**（支付未成功时保单停在待生效即正确的领域状态，补偿由定时激活与
 * billing 逾期失效链路承担），其全部产出是一行结构化日志。故用例锁死它唯一的运行契约：
 * **任何形态的报文都不得抛出** —— 该主题由支付域面向所有业务域共用，本监听器抛出会卡住消费位点，
 * 连带阻塞理赔、保全域对赔付/退费失败的感知。
 * </p>
 * <p>
 * 报文与对端字段的对齐由 {@code PaymentOrderFailedMessageTest} 单独守护。
 * </p>
 */
class PremiumCollectionFailedListenerTest {

    private final PremiumCollectionFailedListener listener = new PremiumCollectionFailedListener();

    @Test
    void shouldNotThrowOnOwnDomainFailureMessage() {
        assertDoesNotThrow(() -> listener.onPaymentFailed(payload("POLICY", "FAILED")));
    }

    /**
     * 人工取消与渠道失败共用主题，消费侧据 resultType 分流日志级别，两者都须可消费。
     */
    @Test
    void shouldNotThrowOnCancelledMessage() {
        assertDoesNotThrow(() -> listener.onPaymentFailed(payload("POLICY", "CANCELLED")));
    }

    /**
     * 该主题是全业务域共用出口：赔付失败（CLAIM）、退费失败（MAINTENANCE）同样到达本监听器，
     * 必须跳过而非按保单ID处理。
     */
    @Test
    void shouldNotThrowOnOtherBusinessDomainMessage() {
        assertDoesNotThrow(() -> listener.onPaymentFailed(payload("CLAIM", "FAILED")));
        assertDoesNotThrow(() -> listener.onPaymentFailed(payload("MAINTENANCE", "FAILED")));
    }

    @Test
    void shouldNotThrowWhenRequiredFieldsMissing() {
        assertDoesNotThrow(() -> listener.onPaymentFailed("{\"businessType\":\"POLICY\",\"reason\":\"余额不足\"}"));
    }

    /**
     * 空串与字面量 {@code null} 报文在 Kafka 中真实存在（生产者异常、清理策略产出），
     * 反序列化结果为 null 而非异常，须走同一套校验分支。
     */
    @Test
    void shouldNotThrowOnMalformedOrEmptyPayload() {
        assertDoesNotThrow(() -> listener.onPaymentFailed("{not-a-json"));
        assertDoesNotThrow(() -> listener.onPaymentFailed(""));
        assertDoesNotThrow(() -> listener.onPaymentFailed("null"));
    }

    /**
     * payment 域 {@code payment-order-failed} 主题载荷（字段名与对端 record 逐一对齐）。
     */
    private String payload(String businessType, String resultType) {
        return """
                {"paymentNo":"PAY-NO-001","businessId":"POLICY-001","businessType":"%s",
                 "tenantId":"TENANT-001","resultType":"%s","reason":"渠道返回余额不足",
                 "occurredAt":"2026-09-12T09:30:00"}
                """.formatted(businessType, resultType);
    }
}
