package com.titanium.policy.event;

import java.time.LocalDateTime;

public record PolicySuspendedEvent(String policyId, LocalDateTime suspendedAt, String tenantId) {
}