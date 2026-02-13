package com.titanium.policy.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 签发保单命令
 */
public record IssuePolicyCommand(
        @TargetAggregateIdentifier
        String policyId,
        String operatorId,
        String tenantId
) {}
