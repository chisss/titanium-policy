package com.titanium.policy.event.insurance;

import java.time.LocalDateTime;

import org.axonframework.serialization.Revision;

/**
 * 投保单承保出单事件 - 触发保单创建
 */
@Revision("1")
public record InsuranceIssuedEvent(
        String insuranceId,
        String insuranceNo,
        LocalDateTime issuedTime,
        String tenantId
) {}
