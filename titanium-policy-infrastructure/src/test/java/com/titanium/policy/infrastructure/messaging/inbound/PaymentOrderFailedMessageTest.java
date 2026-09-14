package com.titanium.policy.infrastructure.messaging.inbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.alibaba.fastjson2.JSONObject;

/**
 * 支付出账未成功防腐消息反序列化用例（policy 域）
 * <p>
 * 锁死本 record 字段名与 payment 域出站载荷逐一对齐：字段名漂移不会编译报错，只会让监听器
 * 拿到 null —— 失败事实仍被记录，但保单ID / 支付单号 / 租户静默丢失，日志里只剩一句无主语的告警，
 * 是本域最容易无声失效的接缝（对端载荷见 payment 域
 * {@code com.titanium.payment.infrastructure.event.PaymentOrderFailedMessage}）。
 * </p>
 * <p>
 * 载荷形态取自对端 {@code JSON.toJSONString(message)}：两个枚举字段（businessType / resultType）
 * 序列化为枚举常量名，与对端 {@code getCode()} 取值一致，故消费侧用字符串常量比对。
 * </p>
 */
class PaymentOrderFailedMessageTest {

    @Test
    void shouldDeserializePaymentDomainPayload() {
        PaymentOrderFailedMessage message = JSONObject.parseObject(payload(), PaymentOrderFailedMessage.class);

        assertEquals("PAY-NO-001", message.paymentNo());
        assertEquals("POLICY-001", message.businessId(), "businessId 是本域定位保单的唯一依据");
        assertEquals("POLICY", message.businessType(), "过滤本域消息的依据，缺失会把赔付失败误判为保费未收");
        assertEquals("TENANT-001", message.tenantId(), "消费线程无租户上下文，载荷是唯一归属依据");
        assertEquals("FAILED", message.resultType(), "对端 PaymentOrderStatus.FAILED 的常量名");
        assertEquals("渠道返回余额不足", message.reason());
        assertEquals(LocalDateTime.of(2026, 9, 12, 9, 30), message.occurredAt());
    }

    /**
     * 人工取消与渠道失败共用同一主题，仅 {@code resultType} 可辨：消费侧据此分流日志级别。
     */
    @Test
    void shouldKeepBothFailureKindsDistinguishable() {
        PaymentOrderFailedMessage cancelled = JSONObject.parseObject(
                payload().replace("\"FAILED\"", "\"CANCELLED\""), PaymentOrderFailedMessage.class);

        assertEquals("CANCELLED", cancelled.resultType(), "取消与失败共用载荷，成因不可丢");
    }

    /**
     * 对端新增字段（前向兼容）：未声明字段必须被忽略而非解析失败——跨域载荷由对端独立演进，
     * 本域 record 只能尾部兼容，不能因对端加字段而丢整条消息。
     */
    @Test
    void shouldIgnoreUnknownFieldsFromCounterpart() {
        PaymentOrderFailedMessage message = JSONObject.parseObject(
                payload().replace("{\"paymentNo\"", "{\"channelCode\":\"WECHAT\",\"paymentNo\""),
                PaymentOrderFailedMessage.class);

        assertEquals("PAY-NO-001", message.paymentNo());
        assertEquals("POLICY-001", message.businessId());
    }

    /**
     * 字段缺失时对应组件为 null（而非整条解析失败），由监听器做必填校验后再决定是否记录。
     */
    @Test
    void shouldTolerateMissingOptionalFields() {
        PaymentOrderFailedMessage message = JSONObject.parseObject(
                "{\"paymentNo\":\"PAY-NO-002\",\"businessId\":\"POLICY-002\"}", PaymentOrderFailedMessage.class);

        assertNull(message.businessType());
        assertNull(message.reason());
        assertNull(message.occurredAt());
    }

    /**
     * payment 域 {@code payment-order-failed} 主题载荷（字段名与对端 record 逐一对齐）。
     */
    private String payload() {
        return """
                {"paymentNo":"PAY-NO-001","businessId":"POLICY-001","businessType":"POLICY",
                 "tenantId":"TENANT-001","resultType":"FAILED","reason":"渠道返回余额不足",
                 "occurredAt":"2026-09-12T09:30:00"}
                """;
    }
}
