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

    /**
     * 被保险人集合元素标识：优先复用聚合内 {@code insuredId}（字段目录声明的 identityField），
     * 标识缺失或超长时退化为「保单 + 顺序」派生值。
     * <p>
     * 🔴 <b>派生前缀 {@code ":I:"} 与读模型 {@code PolicyInsuredView} 主键生成逐字一致</b>——
     * 读侧投影与写侧执行器必须使用同一函数，否则快照发布的对象标识执行侧解析不到，
     * 受理成功的保全项会在生效环节必然失败。
     * </p>
     *
     * @param policyId 保单ID
     * @param insured  聚合内被保险人快照
     * @param index    被保险人清单顺序（仅退化路径使用）
     * @return 集合元素对象标识
     */
    public static PolicyMaintenanceObjectId insured(
            String policyId,
            InsuredPartyList.InsuredInfo insured,
            int index) {
        String insuredId = insured == null ? null : insured.insuredId();
        if (insuredId != null && PROJECTION_ID.matcher(insuredId).matches()) {
            return new PolicyMaintenanceObjectId(insuredId);
        }
        String seed = policyId + ":I:" + index;
        String derived = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        return new PolicyMaintenanceObjectId(derived);
    }
}
