package com.titanium.policy.api.request.maintenance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Maintenance 提交给 Policy 的追溯生效权威证据引用。 */
public record PolicyMaintenanceRetroactiveEvidenceRequest(
        @NotBlank @Size(max = 64) String analysisId,
        @Positive int analysisVersion,
        @NotBlank @Size(min = 64, max = 64) String analysisResultHash,
        @NotBlank @Size(max = 64) String periodRecalculationId,
        @Positive int periodRecalculationVersion,
        @NotBlank @Size(max = 64) String productRecalculationId,
        @NotBlank @Size(max = 64) String productRecalculationVersion,
        @NotBlank @Size(min = 64, max = 64) String productInputHash,
        @NotBlank @Size(min = 64, max = 64) String productResultHash,
        @NotBlank @Size(max = 64) String billingBatchId,
        @NotBlank @Size(min = 64, max = 64) String billingBatchResultHash,
        @NotBlank @Size(max = 32) String billingStatus,
        @Size(max = 64) String billingResolutionId,
        @Size(min = 64, max = 64) String billingResolutionResultHash,
        @Size(max = 16) String targetAccountingPeriod,
        @PositiveOrZero int resolvedLineCount) {
}
