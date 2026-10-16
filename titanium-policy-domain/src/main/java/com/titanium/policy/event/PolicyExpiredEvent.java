package com.titanium.policy.event;

import java.time.LocalDateTime;

import org.axonframework.serialization.Revision;

@Revision("1")
public record PolicyExpiredEvent(String policyId, LocalDateTime expiredAt, String tenantId) {
}
