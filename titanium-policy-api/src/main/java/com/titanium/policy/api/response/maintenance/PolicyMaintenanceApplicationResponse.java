package com.titanium.policy.api.response.maintenance;

import java.time.LocalDateTime;
import java.util.List;

import com.titanium.metadata.enums.maintenance.PolicyMaintenanceAction;

import io.swagger.v3.oas.annotations.media.Schema;

/** Policy 成功应用保全变更后的权威回执。 */
@Schema(description = "Policy 保全应用权威回执")
public record PolicyMaintenanceApplicationResponse(
        String requestId,
        String endorsementNo,
        long expectedPolicyVersion,
        long actualPolicyVersion,
        String applicationHash,
        PolicyMaintenanceAppliedSnapshotResponse appliedSnapshot,
        List<PolicyMaintenanceAppliedFieldResponse> appliedFields,
        LocalDateTime appliedAt,
        PolicyMaintenanceAction stateAction,
        String statusBefore,
        String statusAfter,
        PolicyMaintenanceRetroactiveEvidenceResponse retroactiveEvidence) {

    /** 兼容 M5-02 字段型响应构造。 */
    public PolicyMaintenanceApplicationResponse(
            String requestId,
            String endorsementNo,
            long expectedPolicyVersion,
            long actualPolicyVersion,
            String applicationHash,
            PolicyMaintenanceAppliedSnapshotResponse appliedSnapshot,
            List<PolicyMaintenanceAppliedFieldResponse> appliedFields,
            LocalDateTime appliedAt) {
        this(requestId, endorsementNo, expectedPolicyVersion, actualPolicyVersion, applicationHash,
                appliedSnapshot, appliedFields, appliedAt, PolicyMaintenanceAction.NONE, null, null, null);
    }

    /** 兼容 M5-03 状态类响应。 */
    public PolicyMaintenanceApplicationResponse(
            String requestId,
            String endorsementNo,
            long expectedPolicyVersion,
            long actualPolicyVersion,
            String applicationHash,
            PolicyMaintenanceAppliedSnapshotResponse appliedSnapshot,
            List<PolicyMaintenanceAppliedFieldResponse> appliedFields,
            LocalDateTime appliedAt,
            PolicyMaintenanceAction stateAction,
            String statusBefore,
            String statusAfter) {
        this(requestId, endorsementNo, expectedPolicyVersion, actualPolicyVersion, applicationHash,
                appliedSnapshot, appliedFields, appliedAt, stateAction, statusBefore, statusAfter, null);
    }
}
