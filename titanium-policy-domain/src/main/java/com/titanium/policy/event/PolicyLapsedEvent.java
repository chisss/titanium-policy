package com.titanium.policy.event;

import java.time.LocalDateTime;

/**
 * 保单已失效事件（宽限期满未缴费，进入中止状态）
 */
public record PolicyLapsedEvent(String policyId, String reason, LocalDateTime lapsedAt, String operatorId,
                                String tenantId) {
}
