package com.titanium.policy.event;

import java.time.LocalDateTime;
import java.util.List;

import com.titanium.metadata.enums.maintenance.PolicyMaintenanceAction;
import com.titanium.metadata.enums.policy.PolicyEnum.TerminationReason;
import com.titanium.policy.common.enums.EndorsementCategory;
import com.titanium.policy.valueobject.PolicyStatus;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceAppliedField;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceExecutionState;

/** Policy 在单一事件中原子应用案件字段与合同状态动作。 */
public record PolicyMaintenanceStateAppliedEvent(
        String policyId,
        String requestId,
        String requestPayloadHash,
        String sourceMaintenanceId,
        String endorsementNo,
        String applicationType,
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
        PolicyMaintenanceAction stateAction,
        PolicyStatus.StatusCode statusBefore,
        PolicyStatus.StatusCode statusAfter,
        String stateReason,
        TerminationReason terminationReason,
        LocalDateTime appliedAt,
        String operatorId,
        String tenantId) {

    public PolicyMaintenanceStateAppliedEvent {
        appliedFields = List.copyOf(appliedFields);
    }
}
