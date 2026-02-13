package com.titanium.policy.event;

import java.time.LocalDateTime;

/**
 * 保单签发事件
 */
public record PolicyIssuedEvent(
        String policyId,
        String policyNo,
        LocalDateTime issueTime,
        String operatorId,
        String tenantId
) {}
