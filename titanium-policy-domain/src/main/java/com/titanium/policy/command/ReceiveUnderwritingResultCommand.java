package com.titanium.policy.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.policy.valueobject.insurance.UnderwritingResult;

/**
 * 接收核保结果命令
 */
public record ReceiveUnderwritingResultCommand(
        @TargetAggregateIdentifier
        String insuranceId,
        UnderwritingResult underwritingResult,
        String tenantId
) {}
