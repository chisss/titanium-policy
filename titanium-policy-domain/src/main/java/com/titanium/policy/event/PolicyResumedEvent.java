package com.titanium.policy.event;

import java.time.LocalDateTime;

public record PolicyResumedEvent(String policyId, LocalDateTime resumedAt, String tenantId) {
}