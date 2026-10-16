package com.titanium.policy.event;

import java.time.LocalDateTime;

import org.axonframework.serialization.Revision;

@Revision("1")
public record PolicyResumedEvent(String policyId, LocalDateTime resumedAt, String tenantId) {
}
