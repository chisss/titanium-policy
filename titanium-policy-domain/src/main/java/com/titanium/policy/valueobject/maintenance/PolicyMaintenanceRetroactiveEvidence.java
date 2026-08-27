package com.titanium.policy.valueobject.maintenance;

import java.util.Locale;

import com.titanium.policy.exception.PolicyBusinessRuleException;

/** Policy 保存的追溯生效跨域证据引用，不复制 Product/Billing 业务明细。 */
public record PolicyMaintenanceRetroactiveEvidence(
        String analysisId,
        int analysisVersion,
        String analysisResultHash,
        String periodRecalculationId,
        int periodRecalculationVersion,
        String productRecalculationId,
        String productRecalculationVersion,
        String productInputHash,
        String productResultHash,
        String billingBatchId,
        String billingBatchResultHash,
        String billingStatus,
        String billingResolutionId,
        String billingResolutionResultHash,
        String targetAccountingPeriod,
        int resolvedLineCount) {

    public PolicyMaintenanceRetroactiveEvidence {
        analysisId = text(analysisId, "analysisId");
        analysisResultHash = hash(analysisResultHash, "analysisResultHash");
        periodRecalculationId = text(periodRecalculationId, "periodRecalculationId");
        productRecalculationId = text(productRecalculationId, "productRecalculationId");
        productRecalculationVersion = text(productRecalculationVersion, "productRecalculationVersion");
        productInputHash = hash(productInputHash, "productInputHash");
        productResultHash = hash(productResultHash, "productResultHash");
        billingBatchId = text(billingBatchId, "billingBatchId");
        billingBatchResultHash = hash(billingBatchResultHash, "billingBatchResultHash");
        billingStatus = text(billingStatus, "billingStatus").toUpperCase(Locale.ROOT);
        billingResolutionId = normalize(billingResolutionId);
        billingResolutionResultHash = normalizeHash(billingResolutionResultHash);
        targetAccountingPeriod = normalize(targetAccountingPeriod);
        if (analysisVersion < 1 || periodRecalculationVersion < 1 || resolvedLineCount < 0) {
            throw invalid("追溯证据版本或处理行数非法");
        }
        validateBillingResolution(
                billingStatus, billingResolutionId, billingResolutionResultHash,
                targetAccountingPeriod, resolvedLineCount);
    }

    private static void validateBillingResolution(
            String billingStatus,
            String billingResolutionId,
            String billingResolutionResultHash,
            String targetAccountingPeriod,
            int resolvedLineCount) {
        if (!"POSTED".equals(billingStatus) && !"NOT_REQUIRED".equals(billingStatus)
                && !"REVIEW_REQUIRED".equals(billingStatus)) {
            throw invalid("未知Billing期间调整状态");
        }
        if ("REVIEW_REQUIRED".equals(billingStatus)) {
            if (billingResolutionId == null || billingResolutionResultHash == null
                    || targetAccountingPeriod == null || resolvedLineCount < 1
                    || !targetAccountingPeriod.matches("\\d{4}-(0[1-9]|1[0-2])")) {
                throw invalid("关闭期间批次缺少完整处理结论");
            }
            return;
        }
        if (billingResolutionId != null || billingResolutionResultHash != null
                || targetAccountingPeriod != null || resolvedLineCount != 0) {
            throw invalid("无关闭期间的批次不得携带处理结论");
        }
    }

    private static String text(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalid(field + "不能为空");
        }
        return value.trim();
    }

    private static String hash(String value, String field) {
        String result = text(value, field).toLowerCase(Locale.ROOT);
        if (!result.matches("[a-f0-9]{64}")) {
            throw invalid(field + "必须为SHA-256");
        }
        return result;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeHash(String value) {
        return value == null || value.isBlank() ? null : hash(value, "billingResolutionResultHash");
    }

    private static PolicyBusinessRuleException invalid(String message) {
        return new PolicyBusinessRuleException("POLICY_MAINTENANCE_RETROACTIVE_EVIDENCE_INVALID", message);
    }
}
