package com.titanium.policy.infrastructure.messaging.inbound;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付出账成功防腐入站消息（对端域：payment）
 * <p>
 * payment 域 {@code payment-order-paid} 主题 JSON 载荷镜像，<b>不依赖对端域任何类</b>。
 * 由 {@link com.titanium.policy.infrastructure.messaging.PaymentPaidListener} 一次反序列化后，
 * 经装配器翻译为 {@link com.titanium.policy.valueobject.payment.PremiumCollectionNotice} 派发回写。
 * </p>
 * <p>
 * 字段名与对端 {@code PaymentProcessedEvent} 逐一对齐；{@code businessType} 是本域过滤依据——
 * 该主题是支付域对**所有**业务域出账成功的统一出口（保单域保费收取、理赔域赔付、保全域退费
 * 共用），不过滤会把赔付/退费金额误记为保费收讫。
 * </p>
 *
 * @param paymentNo    支付单号（对账凭证号）
 * @param paymentId    支付流水ID（实收回写的幂等键）
 * @param businessId   关联业务单号（本域为保单ID）
 * @param businessType 关联业务域（本域只处理 {@code POLICY}）
 * @param amount       支付金额
 * @param currency     金额币种（缺失回落人民币，兼容本字段落地前发布的在途消息）
 * @param paidAt       出账成功时间（实收时间）
 * @param tenantId     租户ID
 */
public record PaymentOrderPaidMessage(
        String paymentNo,
        String paymentId,
        String businessId,
        String businessType,
        BigDecimal amount,
        String currency,
        LocalDateTime paidAt,
        String tenantId) {
}
