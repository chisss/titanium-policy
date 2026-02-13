package com.titanium.policy.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 触发承保命令（核保通过后触发）
 */
public record TriggerIssuanceCommand(
        @TargetAggregateIdentifier
        String insuranceId,
        String tenantId
) {}
