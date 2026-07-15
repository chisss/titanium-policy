package com.titanium.policy.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 保单到期满期给付命令（定时任务专用，满期金由聚合自身保额推导）
 * <p>
 * 与 {@link MaturePolicyCommand}（调用方显式传入满期金，供人工/特定金额场景）区别：本命令由满期给付定时任务
 * 在保单止期到达时对生存给付型（两全险）保单批量触发，满期金额取聚合自身基本保额 {@code sumInsured}，
 * 无需调用方提供——避免读模型为批处理泄露保额、金额权威内聚于聚合。校验同满期给付：仅生效两全险、
 * 保额为正方可给付。
 * </p>
 *
 * @param policyId 保单ID
 * @param operatorId 操作人（定时任务系统账号）
 * @param tenantId 租户ID
 */
public record MatureDuePolicyCommand(
        @TargetAggregateIdentifier String policyId,
        String operatorId,
        String tenantId) {
}
