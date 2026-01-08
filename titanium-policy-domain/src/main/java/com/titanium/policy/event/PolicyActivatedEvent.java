package com.titanium.policy.event;

import java.time.LocalDateTime;

public record PolicyActivatedEvent(String policyId, LocalDateTime activatedAt, String tenantId) {
}
