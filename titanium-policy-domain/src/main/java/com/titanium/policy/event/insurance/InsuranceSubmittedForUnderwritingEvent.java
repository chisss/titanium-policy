package com.titanium.policy.event.insurance;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 投保单提交核保事件 - Kafka发布到核保域消费
 */
public record InsuranceSubmittedForUnderwritingEvent(
        String insuranceId,
        String insuranceNo,
        String holderId,
        int insuredCount,
        BigDecimal exactPremium,
        String currency,
        LocalDateTime insurancePeriodStart,
        LocalDateTime insurancePeriodEnd,
        List<String> productCodes,
        int underwritingPriority,
        String policyForm,
        String tenantId
) {}
