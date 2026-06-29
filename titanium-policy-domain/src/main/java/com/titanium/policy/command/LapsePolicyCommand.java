package com.titanium.policy.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 保单失效命令（宽限期满未缴费，由计费/定时任务触发）
 * <p>
 * 寿险特有：保费到期后经宽限期仍未缴，保单进入失效(中止)状态，保障暂停但可经复效恢复。
 * 区别于满期(EXPIRED 终态)与暂停(SUSPENDED 保全主动暂停)。
 * </p>
 */
public record LapsePolicyCommand(
        @TargetAggregateIdentifier
        String policyId,
        String reason,
        String operatorId,
        String tenantId
) {}
