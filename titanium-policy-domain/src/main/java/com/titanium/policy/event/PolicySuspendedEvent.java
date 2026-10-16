package com.titanium.policy.event;

import java.time.LocalDateTime;

import org.axonframework.serialization.Revision;

@Revision("1")
public record PolicySuspendedEvent(String policyId, LocalDateTime suspendedAt, String tenantId) {
}
