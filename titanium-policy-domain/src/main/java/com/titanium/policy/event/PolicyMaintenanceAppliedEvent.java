package com.titanium.policy.event;

import java.time.LocalDateTime;
import java.util.List;

import com.titanium.policy.common.enums.EndorsementCategory;
import com.titanium.policy.common.enums.PolicyDataUpdateType;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceAppliedField;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceExecutionState;

/** Policy 已在单一聚合事件中应用字段、生成批单、版本和实际快照。 */
public record PolicyMaintenanceAppliedEvent(
        String policyId,
        String requestId,
        String requestPayloadHash,
        String sourceMaintenanceId,
        String endorsementNo,
        PolicyDataUpdateType updateType,
        EndorsementCategory category,
        long expectedPolicyVersion,
        long actualPolicyVersion,
        LocalDateTime effectiveAt,
        String changeSummary,
        String proposedSnapshotHash,
        String originalSnapshotHash,
        String appliedSnapshotStorageKey,
        String appliedSnapshotContentHash,
        String applicationHash,
        List<PolicyMaintenanceAppliedField> appliedFields,
        PolicyMaintenanceExecutionState executionStateAfter,
        LocalDateTime appliedAt,
        String operatorId,
        String tenantId) {

    public PolicyMaintenanceAppliedEvent {
        appliedFields = List.copyOf(appliedFields);
    }
}
