package com.titanium.policy.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record ActivatePolicyCommand(@TargetAggregateIdentifier String policyId, String tenantId) {
}
