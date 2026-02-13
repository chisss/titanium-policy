package com.titanium.policy.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 作废意向单命令
 *
 * @param proposalId 意向单ID
 * @param changeReason 作废原因
 * @param tenantId 租户ID
 */
public record VoidProposalCommand(
        @TargetAggregateIdentifier
        String proposalId,
        String changeReason,
        String tenantId
) {}
