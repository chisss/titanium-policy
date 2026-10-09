package com.titanium.policy.api.response.maintenance;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus;
import com.titanium.metadata.enums.policy.PolicyServiceRestrictionType;
import com.titanium.policy.api.response.fieldcatalog.PolicySnapshotFieldValueResponse;

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
        @Schema(description = "生效中的服务受限种类（挂失等），空表示不受限；与 policyStatus 正交")
        List<PolicyServiceRestrictionType> serviceRestrictionTypes,
        Long policyVersion,
        OffsetDateTime businessEffectiveAt,
        OffsetDateTime nextBillingDateAt,
        OffsetDateTime nextPolicyAnniversaryAt,
        String snapshotStorageKey,
        String snapshotContentHash,
        OffsetDateTime capturedAt,
        Map<String, PolicySnapshotFieldValueResponse> fieldValues) {

    public PolicyMaintenanceSnapshotResponse {
        serviceRestrictionTypes = serviceRestrictionTypes == null
                ? List.of() : List.copyOf(serviceRestrictionTypes);
    }

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
                policyStatus, List.of(), policyVersion, businessEffectiveAt, null, null, snapshotStorageKey,
                snapshotContentHash, capturedAt, fieldValues);
    }
}
