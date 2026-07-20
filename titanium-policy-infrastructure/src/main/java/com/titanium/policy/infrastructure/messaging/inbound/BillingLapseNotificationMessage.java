package com.titanium.policy.infrastructure.messaging.inbound;

import java.time.LocalDateTime;

/**
 * 计费失效通知入站消息（policy 域防腐镜像）
 * <p>
 * billing 域经 Kafka 外发的"保费收取失败导致保单失效"通知的 policy 域防腐镜像。
 * <b>不依赖 billing 域任何类</b>，由 fastjson2 一次性反序列化，取代手工逐字段解析。
 * </p>
 * <p>
 * billing 域失效场景：宽限期满仍未缴费，保单进入失效(LAPSED)状态，保障暂停但可经复效恢复。
 * </p>
 *
 * @param policyId      保单ID（billing 域关联的保单标识）
 * @param accountId     计费账户ID（billing 域内部标识）
 * @param reason        失效原因（如"宽限期满未缴保费"）
 * @param timestamp     失效时间戳
 * @param tenantId      租户ID
 */
public record BillingLapseNotificationMessage(
        String policyId,
        String accountId,
        String reason,
        LocalDateTime timestamp,
        String tenantId) {
}
