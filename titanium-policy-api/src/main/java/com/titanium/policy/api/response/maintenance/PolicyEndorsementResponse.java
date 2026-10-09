package com.titanium.policy.api.response.maintenance;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 保单批改权威查询响应。 */
public record PolicyEndorsementResponse(
        String endorsementNo,
        String policyId,
        String updateType,
        String category,
        int policyVersion,
        LocalDateTime effectiveDate,
        String changeSummary,
        String amountItemCode,
        BigDecimal amountValue,
        String amountDirection,
        String amountCurrency,
        boolean requiresPremiumRecalc,
        String sourceMaintenanceId,
        String operatorId,
        LocalDateTime endorsedAt,
        String tenantId) {
}
