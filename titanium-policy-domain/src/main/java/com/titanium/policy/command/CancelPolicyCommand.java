package com.titanium.policy.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 取消保单命令（仅未生效保单）
 */
public record CancelPolicyCommand(
        @TargetAggregateIdentifier
        String policyId,
        String reason,
        String operatorId,
        String tenantId
) {}
