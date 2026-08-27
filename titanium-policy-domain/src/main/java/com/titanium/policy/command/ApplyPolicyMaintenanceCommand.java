package com.titanium.policy.command;

import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.metadata.enums.maintenance.PolicyMaintenanceAction;
import com.titanium.metadata.enums.policy.PolicyEnum.TerminationReason;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceFieldChange;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceRetroactiveEvidence;

/** 应用已到生效时点的保全案件结构化变更的 Policy 正式命令。 */
public record ApplyPolicyMaintenanceCommand(
        @TargetAggregateIdentifier String policyId,
        String requestId,
        String sourceMaintenanceId,
        long expectedPolicyVersion,
        String requestPayloadHash,
        String proposedSnapshotHash,
        String effectiveTimeType,
        LocalDateTime effectiveAt,
        String changeSummary,
        List<PolicyMaintenanceFieldChange> changes,
        PolicyMaintenanceAction stateAction,
        String stateReason,
        TerminationReason terminationReason,
        PolicyMaintenanceRetroactiveEvidence retroactiveEvidence,
        String operatorId,
        String tenantId) {

    public ApplyPolicyMaintenanceCommand {
        changes = changes == null ? List.of() : List.copyOf(changes);
        stateAction = stateAction == null ? PolicyMaintenanceAction.NONE : stateAction;
    }

    /** 兼容 M5-02 仅结构化字段命令。 */
    public ApplyPolicyMaintenanceCommand(
            String policyId,
            String requestId,
            String sourceMaintenanceId,
            long expectedPolicyVersion,
            String requestPayloadHash,
            String proposedSnapshotHash,
            String effectiveTimeType,
            LocalDateTime effectiveAt,
            String changeSummary,
            List<PolicyMaintenanceFieldChange> changes,
            String operatorId,
            String tenantId) {
        this(policyId, requestId, sourceMaintenanceId, expectedPolicyVersion, requestPayloadHash,
                proposedSnapshotHash, effectiveTimeType, effectiveAt, changeSummary, changes,
                PolicyMaintenanceAction.NONE, null, null, null, operatorId, tenantId);
    }

    /** 兼容 M5-03 状态类命令。 */
    public ApplyPolicyMaintenanceCommand(
            String policyId,
            String requestId,
            String sourceMaintenanceId,
            long expectedPolicyVersion,
            String requestPayloadHash,
            String proposedSnapshotHash,
            String effectiveTimeType,
            LocalDateTime effectiveAt,
            String changeSummary,
            List<PolicyMaintenanceFieldChange> changes,
            PolicyMaintenanceAction stateAction,
            String stateReason,
            TerminationReason terminationReason,
            String operatorId,
            String tenantId) {
        this(policyId, requestId, sourceMaintenanceId, expectedPolicyVersion, requestPayloadHash,
                proposedSnapshotHash, effectiveTimeType, effectiveAt, changeSummary, changes,
                stateAction, stateReason, terminationReason, null, operatorId, tenantId);
    }
}
