package com.titanium.policy.query.query;

/**
 * 保单统计查询（管理后台看板聚合用）
 * <p>
 * 读侧统计查询入参，强制携带 {@code tenantId} 保证多租户隔离。经 QueryGateway 派发到读侧处理器，
 * 聚合读模型表 {@code t_policy_view} 的计数与分布数据。
 * </p>
 *
 * @param tenantId 租户ID
 */
public record FindPolicyStatisticsQuery(String tenantId) {
}
