package com.titanium.policy.valueobject.maintenance;

import java.time.OffsetDateTime;

/** Policy 应用事件形成的实际合同快照引用。 */
public record PolicyMaintenanceSnapshotReference(
        String storageKey,
        String contentHash,
        long policyVersion,
        OffsetDateTime capturedAt) {
}
