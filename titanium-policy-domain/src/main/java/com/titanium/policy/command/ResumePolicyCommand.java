package com.titanium.policy.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 恢复保单命令（保全域触发）
 */
public record ResumePolicyCommand(
        @TargetAggregateIdentifier
        String policyId,
        String reason,
        String operatorId,
        String tenantId
) {}
