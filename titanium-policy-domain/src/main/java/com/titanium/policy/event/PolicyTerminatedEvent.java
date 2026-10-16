package com.titanium.policy.event;

import java.time.LocalDateTime;

import org.axonframework.serialization.Revision;

import com.titanium.metadata.enums.policy.PolicyEnum;

/**
 * 保单终止事件（保全域触发/退保）
 */
@Revision("1")
public record PolicyTerminatedEvent(
        String policyId,
        String reason,
        PolicyEnum.TerminationReason terminationReason,
        LocalDateTime terminatedAt,
        String operatorId,
        String tenantId
) {}
