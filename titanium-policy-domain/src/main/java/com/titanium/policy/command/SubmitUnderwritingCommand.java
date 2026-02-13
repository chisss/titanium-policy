package com.titanium.policy.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 提交核保命令
 */
public record SubmitUnderwritingCommand(
        @TargetAggregateIdentifier
        String insuranceId,
        String tenantId
) {}
