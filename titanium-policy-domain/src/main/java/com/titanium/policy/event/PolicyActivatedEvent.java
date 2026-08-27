package com.titanium.policy.event;

import java.time.LocalDateTime;

public record PolicyActivatedEvent(String policyId, String insuranceId, String bizNo, LocalDateTime activatedAt,
                                   String tenantId) {

    /**
     * 兼容历史事件构造与回放。
     */
    public PolicyActivatedEvent(String policyId, LocalDateTime activatedAt, String tenantId) {
        this(policyId, null, null, activatedAt, tenantId);
    }
}
