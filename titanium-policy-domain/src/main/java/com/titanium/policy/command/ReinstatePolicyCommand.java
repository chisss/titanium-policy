package com.titanium.policy.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 保单复效命令（保全域触发，补缴保费+重新核保通过后）
 * <p>
 * 寿险特有：失效(LAPSED)保单在复效期限内，投保人补缴欠缴保费并通过重新核保后，恢复保障。
 * 由保全域 POLICY_REINSTATEMENT 保全审批执行后触发。
 * </p>
 */
public record ReinstatePolicyCommand(
        @TargetAggregateIdentifier
        String policyId,
        String reason,
        String operatorId,
        String tenantId
) {}
