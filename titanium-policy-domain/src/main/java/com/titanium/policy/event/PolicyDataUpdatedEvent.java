package com.titanium.policy.event;

import java.time.LocalDateTime;

import com.titanium.policy.valueobject.PolicyDataUpdateType;

/**
 * 保单数据变更事件（保全域触发：投保人/受益人/缴费方式/加减保）
 */
public record PolicyDataUpdatedEvent(
        String policyId,
        PolicyDataUpdateType updateType,
        int newVersion,
        LocalDateTime updatedAt,
        String operatorId,
        String tenantId
) {}
