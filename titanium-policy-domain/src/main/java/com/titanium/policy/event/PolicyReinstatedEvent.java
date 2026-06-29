package com.titanium.policy.event;

import java.time.LocalDateTime;

/**
 * 保单已复效事件（失效保单补缴+核保通过后恢复保障）
 */
public record PolicyReinstatedEvent(String policyId, String reason, LocalDateTime reinstatedAt, String operatorId,
                                    String tenantId) {
}
