package com.titanium.policy.event;

import java.time.LocalDateTime;

public record PolicyCancelledEvent(String policyId, LocalDateTime cancelledAt, String tenantId) {
}