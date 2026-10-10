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
import java.util.stream.Collectors;

import com.titanium.metadata.enums.maintenance.PolicyMaintenanceAction;
import com.titanium.metadata.enums.policy.PolicyEnum.TerminationReason;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceAmountFact;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceAppliedField;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceFieldChange;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceRetroactiveEvidence;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceServiceRestriction;
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
                null, null, PolicyMaintenanceAction.NONE, null, null, null);
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
                null, null, stateAction, stateReason, terminationReason, null);
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
            PolicyMaintenanceAmountFact amountFact,
            PolicyMaintenanceServiceRestriction serviceRestriction,
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
        // 🔴 与保全域 PolicyMaintenanceApplicationPort.ApplicationRequest.calculateHash 逐字同序：
        //    该摘要是跨域冻结请求的勾稽依据，两侧任一处增删字段或改动顺序都会让合法请求被判「摘要不一致」。
        // 🔴 条件 append（amountFact 为 null 时整段跳过）：字段型/状态型保全的 canonical 串必须逐字不变，
        //    否则存量已冻结请求与在途重试全部失配。
        if (amountFact != null) {
            append(canonical, amountFact.itemCode());
            append(canonical, amountFact.amount().toPlainString());
            append(canonical, amountFact.direction().getCode());
            append(canonical, amountFact.currency());
            append(canonical, amountFact.sourceRef());
        }
        // 🔴 服务受限事实同纪律：非 null 才追加，字段型/金额型/状态型案件的 canonical 串逐字不变。
        //    取枚举 code 而非 name()——code 是跨域契约且不可改名，name() 会被一次重命名静默改变摘要。
        if (serviceRestriction != null) {
            append(canonical, serviceRestriction.type().getCode());
            append(canonical, serviceRestriction.action().getCode());
        }
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

    // ===== 多主险段快照字段值口径（G11/g11-04 D3，读写两侧共用） =====
    // 🔴 写侧（Policy 聚合）据此构造快照指纹字段，读侧（PolicyQueryServiceImpl）据此构造发布值：
    //    两侧必须同源——若各写一份，同一快照会在两处产出不同的 canonical 值，指纹失配且难排查。
    // 🔴 单主险时三个方法的结果均为「该段原值」：两侧指纹与发布值对存量单主险保单逐字不变。

    /**
     * 按段序号升序拼接多主险段的同一字段（逗号连接）。
     * <p>
     * 调用方负责按段序号（{@code lineNo}）排序后取值。单主险时结果即该段原值；
     * 不用「取首段」（D1 纪律）：多主险时<b>全部</b>主险段都在结果内，任一段变更都会改变结果。
     * </p>
     */
    public static String joinMainProductField(List<String> orderedValues) {
        return String.join(",", orderedValues);
    }

    /**
     * 多主险段保额合计（G11/g11-04 D3，单主险时即该段保额原值）。
     * <p>
     * 全部主险段保额为空时返回 {@code null}（与旧单主险实现的可空语义一致）。
     * 🔴 沿用 {@link Money#add} 的币种一致性检查：多段币种不一致时抛异常——受理（读侧采集）
     * 与生效（写侧回写）两侧行为一致，不会出现「受理放行、生效必败」。
     * </p>
     */
    public static Money totalMainSumInsured(List<Money> mainSumInsured) {
        Money total = null;
        for (Money money : mainSumInsured) {
            if (money == null) {
                continue;
            }
            total = total == null ? money : total.add(money);
        }
        return total;
    }

    /** 多主险段保额合计的 canonical 串（{@link #totalMainSumInsured} 求和后取裸码，全空则 {@code null}）。 */
    public static String totalMainSumInsuredCanonical(List<Money> mainSumInsured) {
        Money total = totalMainSumInsured(mainSumInsured);
        return total == null || total.value() == null ? null : total.value().stripTrailingZeros().toPlainString();
    }

    /**
     * 多主险段的币种集合（去重后升序逗号连接，G11/g11-04 D3）。
     * <p>
     * 单主险时即该段币种原值；全部为空/null 时返回空串（调用方按 {@code null} 落指纹/发布，与旧实现
     * 的可空语义一致）。同保单各段币种应一致（保单级单一币种），此处用集合表达而非求和，
     * 避免 {@code Money.add} 在币种不一致时抛异常导致保全被误阻断。
     * </p>
     */
    public static String distinctCurrencies(List<String> currencies) {
        return currencies.stream()
                .filter(currency -> currency != null && !currency.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.joining(","));
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
