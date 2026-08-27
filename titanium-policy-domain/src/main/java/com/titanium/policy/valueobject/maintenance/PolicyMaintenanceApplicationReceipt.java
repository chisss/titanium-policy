package com.titanium.policy.valueobject.maintenance;

import java.time.LocalDateTime;
import java.util.List;

import com.titanium.metadata.enums.maintenance.PolicyMaintenanceAction;
import com.titanium.policy.valueobject.PolicyStatus;

/** Policy 聚合返回并通过事件重放恢复的保全应用权威回执。 */
public record PolicyMaintenanceApplicationReceipt(
        String requestId,
        String requestPayloadHash,
        String endorsementNo,
        long expectedPolicyVersion,
        long actualPolicyVersion,
        String applicationHash,
        PolicyMaintenanceSnapshotReference appliedSnapshot,
        List<PolicyMaintenanceAppliedField> appliedFields,
        LocalDateTime appliedAt,
        PolicyMaintenanceAction stateAction,
        PolicyStatus.StatusCode statusBefore,
        PolicyStatus.StatusCode statusAfter,
        PolicyMaintenanceRetroactiveEvidence retroactiveEvidence) {

    public PolicyMaintenanceApplicationReceipt {
        appliedFields = List.copyOf(appliedFields);
        stateAction = stateAction == null ? PolicyMaintenanceAction.NONE : stateAction;
    }

    /** 兼容 M5-02 字段型权威回执。 */
    public PolicyMaintenanceApplicationReceipt(
            String requestId,
            String requestPayloadHash,
            String endorsementNo,
            long expectedPolicyVersion,
            long actualPolicyVersion,
            String applicationHash,
            PolicyMaintenanceSnapshotReference appliedSnapshot,
            List<PolicyMaintenanceAppliedField> appliedFields,
            LocalDateTime appliedAt) {
        this(requestId, requestPayloadHash, endorsementNo, expectedPolicyVersion, actualPolicyVersion,
                applicationHash, appliedSnapshot, appliedFields, appliedAt,
                PolicyMaintenanceAction.NONE, null, null, null);
    }

    /** 兼容 M5-03 状态类权威回执。 */
    public PolicyMaintenanceApplicationReceipt(
            String requestId,
            String requestPayloadHash,
            String endorsementNo,
            long expectedPolicyVersion,
            long actualPolicyVersion,
            String applicationHash,
            PolicyMaintenanceSnapshotReference appliedSnapshot,
            List<PolicyMaintenanceAppliedField> appliedFields,
            LocalDateTime appliedAt,
            PolicyMaintenanceAction stateAction,
            PolicyStatus.StatusCode statusBefore,
            PolicyStatus.StatusCode statusAfter) {
        this(requestId, requestPayloadHash, endorsementNo, expectedPolicyVersion, actualPolicyVersion,
                applicationHash, appliedSnapshot, appliedFields, appliedAt,
                stateAction, statusBefore, statusAfter, null);
    }

    public PolicyMaintenanceApplicationReceipt withRetroactiveEvidence(
            PolicyMaintenanceRetroactiveEvidence evidence) {
        return new PolicyMaintenanceApplicationReceipt(
                requestId, requestPayloadHash, endorsementNo, expectedPolicyVersion, actualPolicyVersion,
                applicationHash, appliedSnapshot, appliedFields, appliedAt,
                stateAction, statusBefore, statusAfter, evidence);
    }
}
