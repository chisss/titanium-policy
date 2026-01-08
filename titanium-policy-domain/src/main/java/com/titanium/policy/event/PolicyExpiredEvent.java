package com.titanium.policy.event;

import java.time.LocalDateTime;

public record PolicyExpiredEvent(String policyId, LocalDateTime expiredAt, String tenantId) {
}