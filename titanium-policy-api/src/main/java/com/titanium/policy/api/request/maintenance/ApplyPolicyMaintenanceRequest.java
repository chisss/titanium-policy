package com.titanium.policy.api.request.maintenance;

import java.time.LocalDateTime;
import java.util.List;

import com.titanium.metadata.enums.maintenance.PolicyMaintenanceAction;
import com.titanium.metadata.enums.policy.PolicyEnum.TerminationReason;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 立即应用保全案件结构化变更的正式请求。 */
@Schema(description = "Policy 保全应用请求")
public record ApplyPolicyMaintenanceRequest(
        @NotBlank @Size(max = 128) String requestId,
        @NotBlank @Size(max = 64) String maintenanceCaseId,
        @PositiveOrZero long expectedPolicyVersion,
        @NotBlank @Pattern(regexp = "[a-fA-F0-9]{64}") String requestPayloadHash,
        @NotBlank @Pattern(regexp = "[a-fA-F0-9]{64}") String proposedSnapshotHash,
        @NotBlank @Size(max = 20) String effectiveTimeType,
        LocalDateTime effectiveAt,
        @NotBlank @Size(max = 512) String changeSummary,
        List<@Valid PolicyMaintenanceFieldChangeRequest> changes,
        PolicyMaintenanceAction stateAction,
        @Size(max = 500) String stateReason,
        TerminationReason terminationReason,
        @Valid PolicyMaintenanceRetroactiveEvidenceRequest retroactiveEvidence) {

    public ApplyPolicyMaintenanceRequest {
        changes = changes == null ? List.of() : List.copyOf(changes);
        stateAction = stateAction == null ? PolicyMaintenanceAction.NONE : stateAction;
    }

    /** 兼容 M5-02 仅结构化字段请求。 */
    public ApplyPolicyMaintenanceRequest(
            String requestId,
            String maintenanceCaseId,
            long expectedPolicyVersion,
            String requestPayloadHash,
            String proposedSnapshotHash,
            String effectiveTimeType,
            LocalDateTime effectiveAt,
            String changeSummary,
            List<PolicyMaintenanceFieldChangeRequest> changes) {
        this(requestId, maintenanceCaseId, expectedPolicyVersion, requestPayloadHash,
                proposedSnapshotHash, effectiveTimeType, effectiveAt, changeSummary, changes,
                PolicyMaintenanceAction.NONE, null, null, null);
    }

    /** 兼容 M5-03 状态类请求。 */
    public ApplyPolicyMaintenanceRequest(
            String requestId,
            String maintenanceCaseId,
            long expectedPolicyVersion,
            String requestPayloadHash,
            String proposedSnapshotHash,
            String effectiveTimeType,
            LocalDateTime effectiveAt,
            String changeSummary,
            List<PolicyMaintenanceFieldChangeRequest> changes,
            PolicyMaintenanceAction stateAction,
            String stateReason,
            TerminationReason terminationReason) {
        this(requestId, maintenanceCaseId, expectedPolicyVersion, requestPayloadHash,
                proposedSnapshotHash, effectiveTimeType, effectiveAt, changeSummary, changes,
                stateAction, stateReason, terminationReason, null);
    }
}
