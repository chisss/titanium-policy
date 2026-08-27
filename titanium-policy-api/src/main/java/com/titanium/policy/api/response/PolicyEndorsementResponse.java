package com.titanium.policy.api.response;

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
        boolean requiresPremiumRecalc,
        String sourceMaintenanceId,
        String operatorId,
        LocalDateTime endorsedAt,
        String tenantId) {
}
