package com.titanium.policy.event.insurance;

import java.time.LocalDateTime;

/**
 * 投保单承保出单事件 - 触发保单创建
 */
public record InsuranceIssuedEvent(
        String insuranceId,
        String insuranceNo,
        LocalDateTime issuedTime,
        String tenantId
) {}
