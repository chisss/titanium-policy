package com.titanium.policy.event;

import java.time.LocalDateTime;

import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceRetroactiveEvidence;

/** Policy 已为保全应用回执追加追溯跨域证据。 */
public record PolicyMaintenanceRetroactiveEvidenceRecordedEvent(
        String policyId,
        String requestId,
        String sourceMaintenanceId,
        PolicyMaintenanceRetroactiveEvidence evidence,
        LocalDateTime recordedAt,
        String operatorId,
        String tenantId) {
}
