package com.titanium.policy.event;

import java.time.LocalDateTime;

/**
 * 保单数据变更事件（保全域触发：投保人/受益人/缴费方式/加减保）
 */
public record PolicyDataUpdatedEvent(
        String policyId,
        String updateType,
        int newVersion,
        LocalDateTime updatedAt,
        String operatorId,
        String tenantId
) {}
