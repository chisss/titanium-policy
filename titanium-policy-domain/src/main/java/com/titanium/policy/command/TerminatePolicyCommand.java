package com.titanium.policy.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.metadata.enums.policy.PolicyEnum;

/**
 * 终止保单命令（保全域触发/退保）
 */
public record TerminatePolicyCommand(
        @TargetAggregateIdentifier
        String policyId,
        String reason,
        String operatorId,
        PolicyEnum.TerminationReason terminationReason,
        String tenantId
) {}
