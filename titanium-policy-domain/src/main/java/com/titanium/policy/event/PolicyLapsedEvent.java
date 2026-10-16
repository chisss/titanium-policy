package com.titanium.policy.event;

import java.time.LocalDateTime;

import org.axonframework.serialization.Revision;

/**
 * 保单已失效事件（宽限期满未缴费，进入中止状态）
 */
@Revision("1")
public record PolicyLapsedEvent(String policyId, String reason, LocalDateTime lapsedAt, String operatorId,
                                String tenantId) {
}
