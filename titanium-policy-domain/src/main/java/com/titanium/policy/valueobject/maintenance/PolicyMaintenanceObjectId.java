package com.titanium.policy.valueobject.maintenance;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;

import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.exception.PolicyBusinessRuleException;

/** Policy 集合字段在保全契约中的稳定对象标识。 */
public record PolicyMaintenanceObjectId(String value) {

    private static final int MAX_PROJECTION_ID_LENGTH = 32;
    private static final Pattern PROJECTION_ID = Pattern.compile("[A-Za-z0-9._-]{1,32}");

    public PolicyMaintenanceObjectId {
        if (value == null || value.isBlank() || value.length() > MAX_PROJECTION_ID_LENGTH) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_FIELD_OBJECT_INVALID", "保全集合对象标识必须为 1 到 32 位");
        }
        value = value.trim();
        if (!PROJECTION_ID.matcher(value).matches()) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_FIELD_OBJECT_INVALID", "保全集合对象标识包含非法字符");
        }
    }

    /** 优先复用聚合内标识；存量长标识使用保单和顺序生成稳定兼容标识。 */
    public static PolicyMaintenanceObjectId beneficiary(
            String policyId,
            InsuredPartyList.BeneficiaryInfo beneficiary,
            int index) {
        String beneficiaryId = beneficiary == null ? null : beneficiary.beneficiaryId();
        if (beneficiaryId != null && PROJECTION_ID.matcher(beneficiaryId).matches()) {
            return new PolicyMaintenanceObjectId(beneficiaryId);
        }
        String seed = policyId + ":B:" + index;
        String derived = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        return new PolicyMaintenanceObjectId(derived);
    }
}
