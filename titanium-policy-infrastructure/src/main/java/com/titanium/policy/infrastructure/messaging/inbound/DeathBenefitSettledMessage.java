package com.titanium.policy.infrastructure.messaging.inbound;

/**
 * 身故给付结算事件入站消息（policy 域防腐镜像）
 * <p>
 * claim 域经 Kafka 外发的"身故给付结算完成"事件的 policy 域防腐镜像。<b>不依赖 claim 域任何类</b>，
 * 由 fastjson2 一次性反序列化，取代手工 {@code getString}。claim 域仅贯穿 policyId（裸字符串），
 * tenantId 可能缺失。
 * </p>
 */
public record DeathBenefitSettledMessage(String policyId, String tenantId) {
}
