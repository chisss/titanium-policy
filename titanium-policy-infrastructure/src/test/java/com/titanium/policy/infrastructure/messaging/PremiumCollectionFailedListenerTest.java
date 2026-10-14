package com.titanium.policy.infrastructure.messaging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * 保费收取失败入站监听器用例（policy 域）
 * <p>
 * 本监听器**有意不改动任何状态**（支付未成功时保单停在待生效即正确的领域状态，补偿由定时激活与
 * billing 逾期失效链路承担），其全部产出是一行结构化日志。故用例锁死两条运行契约：
 * ① 可判定为「空载荷/字段缺失/他域消息」的报文正常返回（跳过而非失败）；
 * ② 🔴 报文无法解析等失败**一律上抛**（G16/AC-03）——交 Kafka 容器有界重试（FixedBackOff 3 次）
 * 后进死信主题 {@code payment-order-failed-dlt}，不再就地吞掉。原「抛出会卡住消费位点、连带阻塞
 * 理赔与保全域感知」的顾虑已被 {@code DefaultErrorHandler} 消解：重试耗尽投递死信后位点照常推进。
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
     * 反序列化结果为 null（非异常），走字段缺失分支跳过——不抛、不进死信（可判定跳过 ≠ 失败）。
     */
    @Test
    void shouldNotThrowOnEmptyOrNullLiteralPayload() {
        assertDoesNotThrow(() -> listener.onPaymentFailed(""));
        assertDoesNotThrow(() -> listener.onPaymentFailed("null"));
    }

    /**
     * 畸形 JSON 无法解析，属真失败：一律上抛交容器有界重试 → 死信主题（G16/AC-03），
     * 不再就地吞掉——吞掉会让「解析失败」在死信队列中不可见、不可重放。
     */
    @Test
    void shouldPropagateOnMalformedPayload() {
        assertThrows(RuntimeException.class, () -> listener.onPaymentFailed("{not-a-json"));
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
