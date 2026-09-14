package com.titanium.policy.infrastructure.messaging.inbound;

import java.time.LocalDateTime;

/**
 * 支付出账未成功防腐入站消息（对端域：payment，{@code payment-order-failed} 主题）
 * <p>
 * payment 域该主题 JSON 载荷镜像，<b>不依赖对端域任何类</b>。由
 * {@link com.titanium.policy.infrastructure.messaging.PremiumCollectionFailedListener} 一次反序列化。
 * </p>
 * <p>
 * 对端出站载荷由两种领域事件装配（渠道确认失败 {@code PaymentFailedEvent}、人工取消
 * {@code PaymentCancelledEvent}），二者对消费方是**同一个业务事实**——该笔出款没有成功，
 * 差异仅在 {@link #resultType}。🔴 字段名与对端逐一对齐，改名前须同批核验两端。
 * </p>
 * <p>
 * {@code businessType} 是本域过滤依据——该主题是支付域对**所有**业务域出款未成功的统一出口
 * （保费收取、理赔赔付、保全退费共用），不过滤会把赔付/退费失败误判为保费未收。
 * </p>
 *
 * @param paymentNo    支付单号（对账凭证号）
 * @param businessId   关联业务单号（本域为保单ID）
 * @param businessType 关联业务域（本域只处理 {@code POLICY}）
 * @param tenantId     租户ID（消费线程无租户上下文，跨域载荷是唯一归属依据）
 * @param resultType   未成功结果类型：{@code FAILED}（渠道确认失败）/ {@code CANCELLED}（人工取消）
 * @param reason       未成功原因（渠道失败原因或取消原因，对端为自由文本）
 * @param occurredAt   结果发生时间
 */
public record PaymentOrderFailedMessage(
        String paymentNo,
        String businessId,
        String businessType,
        String tenantId,
        String resultType,
        String reason,
        LocalDateTime occurredAt) {
}
