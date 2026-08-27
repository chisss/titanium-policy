package com.titanium.policy.api.response.maintenance;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** Policy 应用事件形成的实际合同快照引用。 */
@Schema(description = "Policy 保全实际快照引用")
public record PolicyMaintenanceAppliedSnapshotResponse(
        String storageKey,
        String contentHash,
        long policyVersion,
        OffsetDateTime capturedAt) {
}
