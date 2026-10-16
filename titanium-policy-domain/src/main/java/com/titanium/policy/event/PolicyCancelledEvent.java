package com.titanium.policy.event;

import java.time.LocalDateTime;

import org.axonframework.serialization.Revision;

@Revision("1")
public record PolicyCancelledEvent(String policyId, LocalDateTime cancelledAt, String tenantId) {
}
