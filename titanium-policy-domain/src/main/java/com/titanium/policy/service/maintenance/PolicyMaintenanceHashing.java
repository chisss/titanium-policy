package com.titanium.policy.service.maintenance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.titanium.metadata.enums.maintenance.PolicyMaintenanceAction;
import com.titanium.metadata.enums.policy.PolicyEnum.TerminationReason;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceAppliedField;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceFieldChange;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceRetroactiveEvidence;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceSnapshotFieldValue;

/** Policy 保全请求、应用结果和合同快照的稳定摘要算法。 */
public final class PolicyMaintenanceHashing {

    private PolicyMaintenanceHashing() {
    }

    public static String requestHash(
            String tenantId,
            String policyId,
            String requestId,
            String maintenanceCaseId,
            long expectedVersion,
            String proposedSnapshotHash,
            String effectiveTimeType,
            LocalDateTime effectiveAt,
            String changeSummary,
            List<PolicyMaintenanceFieldChange> changes) {
        return requestHash(tenantId, policyId, requestId, maintenanceCaseId, expectedVersion,
                proposedSnapshotHash, effectiveTimeType, effectiveAt, changeSummary, changes,
                PolicyMaintenanceAction.NONE, null, null, null);
    }

    public static String requestHash(
            String tenantId,
            String policyId,
            String requestId,
            String maintenanceCaseId,
            long expectedVersion,
            String proposedSnapshotHash,
            String effectiveTimeType,
            LocalDateTime effectiveAt,
            String changeSummary,
            List<PolicyMaintenanceFieldChange> changes,
            PolicyMaintenanceAction stateAction,
            String stateReason,
            TerminationReason terminationReason) {
        return requestHash(tenantId, policyId, requestId, maintenanceCaseId, expectedVersion,
                proposedSnapshotHash, effectiveTimeType, effectiveAt, changeSummary, changes,
                stateAction, stateReason, terminationReason, null);
    }

    public static String requestHash(
            String tenantId,
            String policyId,
            String requestId,
            String maintenanceCaseId,
            long expectedVersion,
            String proposedSnapshotHash,
            String effectiveTimeType,
            LocalDateTime effectiveAt,
            String changeSummary,
            List<PolicyMaintenanceFieldChange> changes,
            PolicyMaintenanceAction stateAction,
            String stateReason,
            TerminationReason terminationReason,
            PolicyMaintenanceRetroactiveEvidence retroactiveEvidence) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, tenantId);
        append(canonical, policyId);
        append(canonical, requestId);
        append(canonical, maintenanceCaseId);
        append(canonical, Long.toString(expectedVersion));
        append(canonical, proposedSnapshotHash);
        append(canonical, effectiveTimeType);
        append(canonical, effectiveAt == null ? null : effectiveAt.toString());
        append(canonical, changeSummary);
        changes.stream()
                .sorted(Comparator.comparing(PolicyMaintenanceFieldChange::fieldCode)
                        .thenComparing(PolicyMaintenanceFieldChange::objectId))
                .forEach(field -> {
                    append(canonical, field.itemCode());
                    append(canonical, field.objectId());
                    append(canonical, field.fieldCode());
                    append(canonical, field.dataType());
                    append(canonical, field.canonicalValue());
                });
        PolicyMaintenanceAction normalizedAction = stateAction == null
                ? PolicyMaintenanceAction.NONE : stateAction;
        if (normalizedAction.changesStatus()) {
            append(canonical, normalizedAction.name());
            append(canonical, stateReason);
            append(canonical, terminationReason == null ? null : terminationReason.getCode());
        }
        appendRetroactiveEvidence(canonical, retroactiveEvidence);
        return sha256(canonical.toString());
    }

    private static void appendRetroactiveEvidence(
            StringBuilder canonical,
            PolicyMaintenanceRetroactiveEvidence evidence) {
        if (evidence == null) {
            return;
        }
        append(canonical, evidence.analysisId());
        append(canonical, Integer.toString(evidence.analysisVersion()));
        append(canonical, evidence.analysisResultHash());
        append(canonical, evidence.periodRecalculationId());
        append(canonical, Integer.toString(evidence.periodRecalculationVersion()));
        append(canonical, evidence.productRecalculationId());
        append(canonical, evidence.productRecalculationVersion());
        append(canonical, evidence.productInputHash());
        append(canonical, evidence.productResultHash());
        append(canonical, evidence.billingBatchId());
        append(canonical, evidence.billingBatchResultHash());
        append(canonical, evidence.billingStatus());
        append(canonical, evidence.billingResolutionId());
        append(canonical, evidence.billingResolutionResultHash());
        append(canonical, evidence.targetAccountingPeriod());
        append(canonical, Integer.toString(evidence.resolvedLineCount()));
    }

    public static String snapshotHash(
            String tenantId,
            String policyId,
            long policyVersion,
            String productId,
            String productVersion,
            String planVersion,
            Map<String, PolicyMaintenanceSnapshotFieldValue> fields) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, tenantId);
        append(canonical, policyId);
        append(canonical, Long.toString(policyVersion));
        append(canonical, productId);
        append(canonical, productVersion);
        append(canonical, planVersion);
        new TreeMap<>(fields).forEach((fieldCode, fieldValue) -> {
            append(canonical, fieldCode);
            append(canonical, fieldValue.dataType());
            append(canonical, fieldValue.canonicalValue());
        });
        return sha256(canonical.toString());
    }

    public static String applicationHash(
            String requestId,
            String endorsementNo,
            long expectedVersion,
            long actualVersion,
            String snapshotHash,
            List<PolicyMaintenanceAppliedField> appliedFields) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, requestId);
        append(canonical, endorsementNo);
        append(canonical, Long.toString(expectedVersion));
        append(canonical, Long.toString(actualVersion));
        append(canonical, snapshotHash);
        appliedFields.stream()
                .sorted(Comparator.comparing(PolicyMaintenanceAppliedField::fieldCode)
                        .thenComparing(PolicyMaintenanceAppliedField::objectId))
                .forEach(field -> {
                    append(canonical, field.itemCode());
                    append(canonical, field.objectId());
                    append(canonical, field.fieldCode());
                    append(canonical, field.dataType());
                    append(canonical, field.canonicalValue());
                });
        return sha256(canonical.toString());
    }

    public static String stableEndorsementNo(String tenantId, String policyId, String requestId) {
        return "END-" + sha256(tenantId + "\n" + policyId + "\n" + requestId)
                .substring(0, 24).toUpperCase();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK缺少SHA-256实现", exception);
        }
    }

    private static void append(StringBuilder target, String value) {
        target.append(value == null ? -1 : value.length()).append(':');
        if (value != null) {
            target.append(value);
        }
        target.append('\n');
    }
}
