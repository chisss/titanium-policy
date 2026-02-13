package com.titanium.policy.event;

import java.time.LocalDateTime;

/**
 * 保单终止事件（保全域触发/退保）
 */
public record PolicyTerminatedEvent(
        String policyId,
        String reason,
        String terminationReason,
        LocalDateTime terminatedAt,
        String operatorId,
        String tenantId
) {}
