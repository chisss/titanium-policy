package com.titanium.policy.api.response;

import java.time.OffsetDateTime;
import java.util.Map;

import com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/** 保全建案专用 Policy 权威快照响应。 */
@Schema(description = "保全建案专用 Policy 权威快照")
public record PolicyMaintenanceSnapshotResponse(
        String tenantId,
        String policyId,
        String policyNumber,
        String customerId,
        String productId,
        String productVersion,
        String planVersion,
        PolicyStatus policyStatus,
        Long policyVersion,
        OffsetDateTime businessEffectiveAt,
        OffsetDateTime nextBillingDateAt,
        OffsetDateTime nextPolicyAnniversaryAt,
        String snapshotStorageKey,
        String snapshotContentHash,
        OffsetDateTime capturedAt,
        Map<String, PolicySnapshotFieldValueResponse> fieldValues) {

    /** 兼容 M5-04 之前不含未来计划日期的客户端测试构造。 */
    public PolicyMaintenanceSnapshotResponse(
            String tenantId,
            String policyId,
            String policyNumber,
            String customerId,
            String productId,
            String productVersion,
            String planVersion,
            PolicyStatus policyStatus,
            Long policyVersion,
            OffsetDateTime businessEffectiveAt,
            String snapshotStorageKey,
            String snapshotContentHash,
            OffsetDateTime capturedAt,
            Map<String, PolicySnapshotFieldValueResponse> fieldValues) {
        this(tenantId, policyId, policyNumber, customerId, productId, productVersion, planVersion,
                policyStatus, policyVersion, businessEffectiveAt, null, null, snapshotStorageKey,
                snapshotContentHash, capturedAt, fieldValues);
    }
}
