package com.titanium.policy.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.axonframework.serialization.Revision;

/**
 * 保单缴费记录事件
 */
@Revision("1")
public record PolicyPaymentRecordedEvent(
        String policyId,
        String paymentId,
        BigDecimal paymentAmount,
        String currency,
        LocalDateTime paymentTime,
        String tenantId
) {}
