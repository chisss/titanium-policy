package com.titanium.policy.query.result;

import java.time.OffsetDateTime;
import java.util.Map;

import com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus;

/** 保全建案使用的 Policy 权威只读快照。 */
public record PolicyMaintenanceSnapshotQueryResult(
        String tenantId,
        String policyId,
        String policyNumber,
        String customerId,
        String productId,
        String productVersion,
        String planVersion,
        PolicyStatus policyStatus,
        long policyVersion,
        OffsetDateTime businessEffectiveAt,
        OffsetDateTime nextBillingDateAt,
        OffsetDateTime nextPolicyAnniversaryAt,
        String snapshotStorageKey,
        String snapshotContentHash,
        OffsetDateTime capturedAt,
        Map<String, PolicySnapshotFieldValueQueryResult> fieldValues) {

    /** 兼容 M5-04 之前不含未来计划日期的内部构造。 */
    public PolicyMaintenanceSnapshotQueryResult(
            String tenantId,
            String policyId,
            String policyNumber,
            String customerId,
            String productId,
            String productVersion,
            String planVersion,
            PolicyStatus policyStatus,
            long policyVersion,
            OffsetDateTime businessEffectiveAt,
            String snapshotStorageKey,
            String snapshotContentHash,
            OffsetDateTime capturedAt,
            Map<String, PolicySnapshotFieldValueQueryResult> fieldValues) {
        this(tenantId, policyId, policyNumber, customerId, productId, productVersion, planVersion,
                policyStatus, policyVersion, businessEffectiveAt, null, null, snapshotStorageKey,
                snapshotContentHash, capturedAt, fieldValues);
    }

    /** 保单结构化字段值；类型码与保全域 PolicyFieldDataType 对齐。 */
    public record PolicySnapshotFieldValueQueryResult(
            String dataType,
            String canonicalValue,
            String objectId) {

        /** 兼容不含集合对象身份的标量字段内部构造。 */
        public PolicySnapshotFieldValueQueryResult(String dataType, String canonicalValue) {
            this(dataType, canonicalValue, null);
        }
    }
}
