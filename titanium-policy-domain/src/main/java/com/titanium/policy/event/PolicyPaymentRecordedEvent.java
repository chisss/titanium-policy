package com.titanium.policy.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 保单缴费记录事件
 */
public record PolicyPaymentRecordedEvent(
        String policyId,
        String paymentId,
        BigDecimal paymentAmount,
        String currency,
        LocalDateTime paymentTime,
        String tenantId
) {}
