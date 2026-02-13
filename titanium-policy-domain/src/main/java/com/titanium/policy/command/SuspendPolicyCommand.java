package com.titanium.policy.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 暂停保单命令（保全域触发）
 */
public record SuspendPolicyCommand(
        @TargetAggregateIdentifier
        String policyId,
        String reason,
        String operatorId,
        String tenantId
) {}
