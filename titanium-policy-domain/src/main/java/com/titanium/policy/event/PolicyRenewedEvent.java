package com.titanium.policy.event;

import java.time.LocalDateTime;

/**
 * 保单续保事件
 */
public record PolicyRenewedEvent(
        String policyId,
        String newPolicyId,
        LocalDateTime renewedAt,
        String tenantId
) {}
