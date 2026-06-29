package com.titanium.policy.infrastructure.messaging;

import java.time.LocalDateTime;

/**
 * 保全回写上下文
 * <p>
 * 从保全执行事件解析出的、驱动保单状态变更/批改所需的最小上下文。作为防腐层内部模型，
 * 隔离 maintenance 域事件结构，policy 命令翻译只依赖本上下文。
 * </p>
 *
 * @param policyId 保单ID
 * @param operatorId 操作人（保全执行人）
 * @param reason 变更原因（执行明细）
 * @param tenantId 租户ID
 * @param maintenanceType 保全类型编码（批改回写据此映射批改类型）
 * @param effectiveTime 保全生效时间（批改生效日）
 * @param sourceMaintenanceId 来源保全案件ID（批单溯源）
 */
public record MaintenanceWriteBackContext(
        String policyId,
        String operatorId,
        String reason,
        String tenantId,
        String maintenanceType,
        LocalDateTime effectiveTime,
        String sourceMaintenanceId) {
}
