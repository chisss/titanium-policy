package com.titanium.policy.api.response.maintenance;

/** Policy 已记录的追溯生效权威证据引用。 */
public record PolicyMaintenanceRetroactiveEvidenceResponse(
        String analysisId,
        int analysisVersion,
        String analysisResultHash,
        String periodRecalculationId,
        int periodRecalculationVersion,
        String productRecalculationId,
        String productRecalculationVersion,
        String productInputHash,
        String productResultHash,
        String billingBatchId,
        String billingBatchResultHash,
        String billingStatus,
        String billingResolutionId,
        String billingResolutionResultHash,
        String targetAccountingPeriod,
        int resolvedLineCount) {
}
