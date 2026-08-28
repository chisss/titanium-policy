package com.titanium.policy.fieldcatalog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldObjectType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;

/** Policy 对外发布的不可变字段目录。 */
public record PolicyFieldCatalog(String catalogVersion, String contentHash, List<PolicyFieldDescriptor> fields) {

    public static final String STANDARD_VERSION = "2026.08.28.1";

    public PolicyFieldCatalog {
        if (catalogVersion == null || catalogVersion.isBlank()) {
            throw new PolicyFieldCatalogValidationException("字段目录版本不能为空");
        }
        if (fields == null || fields.isEmpty()) {
            throw new PolicyFieldCatalogValidationException("字段目录不能为空");
        }
        catalogVersion = catalogVersion.trim();
        if (fields.stream().anyMatch(Objects::isNull)) {
            throw new PolicyFieldCatalogValidationException("字段目录不能包含空字段");
        }
        fields = fields.stream().sorted(Comparator.comparing(PolicyFieldDescriptor::fieldCode)).toList();
        validateUniqueFieldCodes(fields);
        String calculatedHash = calculateHash(catalogVersion, fields);
        if (contentHash != null && !contentHash.isBlank() && !calculatedHash.equals(contentHash)) {
            throw new PolicyFieldCatalogValidationException("字段目录内容哈希不一致");
        }
        contentHash = calculatedHash;
    }

    /** 创建首版平台标准字段目录。 */
    public static PolicyFieldCatalog standardV1() {
        return new PolicyFieldCatalog(STANDARD_VERSION, null, standardFields());
    }

    /** 按稳定字段码查找字段。 */
    public PolicyFieldDescriptor requireField(String fieldCode) {
        return fields.stream()
                .filter(field -> field.fieldCode().equals(fieldCode))
                .findFirst()
                .orElseThrow(() -> new PolicyFieldCatalogValidationException("字段目录不存在字段: " + fieldCode));
    }

    private static List<PolicyFieldDescriptor> standardFields() {
        return List.of(
                scalar("policy.holder.name", PolicyFieldObjectType.POLICY_HOLDER, PolicyFieldValueType.TEXT,
                        "policy.field.holder.name", proposal("HOLDER_CHANGE", false, false),
                        PolicyFieldSensitivityLevel.SENSITIVE, PolicyFieldMaskingPolicy.NAME),
                scalar("policy.holder.gender", PolicyFieldObjectType.POLICY_HOLDER, PolicyFieldValueType.ENUM,
                        "policy.field.holder.gender", proposal("HOLDER_CHANGE", false, false),
                        PolicyFieldSensitivityLevel.SENSITIVE, PolicyFieldMaskingPolicy.PARTIAL_TEXT),
                scalar("policy.holder.birthDate", PolicyFieldObjectType.POLICY_HOLDER, PolicyFieldValueType.DATE,
                        "policy.field.holder.birthDate", proposal("HOLDER_CHANGE", false, false),
                        PolicyFieldSensitivityLevel.SENSITIVE, PolicyFieldMaskingPolicy.DATE),
                scalar("policy.holder.documentType", PolicyFieldObjectType.POLICY_HOLDER, PolicyFieldValueType.ENUM,
                        "policy.field.holder.documentType", proposal("HOLDER_CHANGE", false, false),
                        PolicyFieldSensitivityLevel.INTERNAL, PolicyFieldMaskingPolicy.NONE),
                scalar("policy.holder.documentNumber", PolicyFieldObjectType.POLICY_HOLDER, PolicyFieldValueType.TEXT,
                        "policy.field.holder.documentNumber", proposal("HOLDER_CHANGE", false, false),
                        PolicyFieldSensitivityLevel.RESTRICTED, PolicyFieldMaskingPolicy.ID_NUMBER),
                scalar("policy.holder.mobile", PolicyFieldObjectType.POLICY_HOLDER, PolicyFieldValueType.TEXT,
                        "policy.field.holder.mobile", executable("POLICY_INFO_CHANGE", true, false),
                        PolicyFieldSensitivityLevel.SENSITIVE, PolicyFieldMaskingPolicy.MOBILE),
                scalar("policy.holder.email", PolicyFieldObjectType.POLICY_HOLDER, PolicyFieldValueType.TEXT,
                        "policy.field.holder.email", proposal("POLICY_INFO_CHANGE", true, false),
                        PolicyFieldSensitivityLevel.SENSITIVE, PolicyFieldMaskingPolicy.EMAIL),
                scalar("policy.holder.address", PolicyFieldObjectType.POLICY_HOLDER, PolicyFieldValueType.TEXT,
                        "policy.field.holder.address", proposal("POLICY_INFO_CHANGE", true, false),
                        PolicyFieldSensitivityLevel.SENSITIVE, PolicyFieldMaskingPolicy.ADDRESS),
                collection("policy.insured.name", PolicyFieldObjectType.INSURED, PolicyFieldValueType.TEXT,
                        "policy.field.insured.name", "insuredId", proposal("INSURED_INFO_CHANGE", false, true),
                        PolicyFieldSensitivityLevel.SENSITIVE, PolicyFieldMaskingPolicy.NAME),
                collection("policy.insured.documentNumber", PolicyFieldObjectType.INSURED, PolicyFieldValueType.TEXT,
                        "policy.field.insured.documentNumber", "insuredId",
                        proposal("INSURED_INFO_CHANGE", false, true), PolicyFieldSensitivityLevel.RESTRICTED,
                        PolicyFieldMaskingPolicy.ID_NUMBER),
                collection("policy.beneficiary.name", PolicyFieldObjectType.BENEFICIARY, PolicyFieldValueType.TEXT,
                        "policy.field.beneficiary.name", "beneficiaryId",
                        executable("BENEFICIARY_CHANGE", false, true), PolicyFieldSensitivityLevel.SENSITIVE,
                        PolicyFieldMaskingPolicy.NAME),
                collection("policy.beneficiary.relationship", PolicyFieldObjectType.BENEFICIARY,
                        PolicyFieldValueType.ENUM, "policy.field.beneficiary.relationship", "beneficiaryId",
                        executable("BENEFICIARY_CHANGE", false, true), PolicyFieldSensitivityLevel.INTERNAL,
                        PolicyFieldMaskingPolicy.NONE),
                collection("policy.beneficiary.share", PolicyFieldObjectType.BENEFICIARY,
                        PolicyFieldValueType.DECIMAL, "policy.field.beneficiary.share", "beneficiaryId",
                        executable("BENEFICIARY_CHANGE", false, true), PolicyFieldSensitivityLevel.INTERNAL,
                        PolicyFieldMaskingPolicy.NONE),
                collection("policy.coverage.sumInsured", PolicyFieldObjectType.COVERAGE,
                        PolicyFieldValueType.DECIMAL, "policy.field.coverage.sumInsured", "policyProductId",
                        executable("COVERAGE_AMOUNT_CHANGE", false, true), PolicyFieldSensitivityLevel.INTERNAL,
                        PolicyFieldMaskingPolicy.NONE),
                scalar("policy.payment.method", PolicyFieldObjectType.POLICY, PolicyFieldValueType.ENUM,
                        "policy.field.payment.method", proposal("PAYMENT_METHOD_CHANGE", false, false),
                        PolicyFieldSensitivityLevel.INTERNAL, PolicyFieldMaskingPolicy.NONE),
                scalar("policy.payment.accountNumber", PolicyFieldObjectType.PAYMENT_ACCOUNT,
                        PolicyFieldValueType.TEXT, "policy.field.payment.accountNumber",
                        proposal("PAYMENT_METHOD_CHANGE", false, false), PolicyFieldSensitivityLevel.RESTRICTED,
                        PolicyFieldMaskingPolicy.BANK_ACCOUNT),
                scalar("policy.status", PolicyFieldObjectType.POLICY, PolicyFieldValueType.ENUM,
                        "policy.field.status", PolicyFieldCapability.readOnly(),
                        PolicyFieldSensitivityLevel.INTERNAL, PolicyFieldMaskingPolicy.NONE),
                scalar("policy.loan.outstandingAmount", PolicyFieldObjectType.LOAN, PolicyFieldValueType.DECIMAL,
                        "policy.field.loan.outstandingAmount", PolicyFieldCapability.readOnly(),
                        PolicyFieldSensitivityLevel.INTERNAL, PolicyFieldMaskingPolicy.NONE),
                scalar("policy.surrender.reason", PolicyFieldObjectType.POLICY, PolicyFieldValueType.TEXT,
                        "policy.field.surrender.reason", proposal("POLICY_TERMINATION", true, false),
                        PolicyFieldSensitivityLevel.SENSITIVE, PolicyFieldMaskingPolicy.PARTIAL_TEXT));
    }

    private static PolicyFieldDescriptor scalar(
            String code,
            PolicyFieldObjectType objectType,
            PolicyFieldValueType valueType,
            String labelKey,
            PolicyFieldCapability capability,
            PolicyFieldSensitivityLevel sensitivity,
            PolicyFieldMaskingPolicy maskingPolicy) {
        return PolicyFieldDescriptor.scalar(code, objectType, valueType, labelKey, capability, sensitivity,
                maskingPolicy);
    }

    private static PolicyFieldDescriptor collection(
            String code,
            PolicyFieldObjectType objectType,
            PolicyFieldValueType valueType,
            String labelKey,
            String identityField,
            PolicyFieldCapability capability,
            PolicyFieldSensitivityLevel sensitivity,
            PolicyFieldMaskingPolicy maskingPolicy) {
        return PolicyFieldDescriptor.collectionField(code, objectType, valueType, labelKey, identityField,
                capability, sensitivity, maskingPolicy);
    }

    private static PolicyFieldCapability proposal(
            String changeTypeCode, boolean clearable, boolean requiresObjectId) {
        return PolicyFieldCapability.proposable(changeTypeCode, clearable, requiresObjectId);
    }

    private static PolicyFieldCapability executable(
            String changeTypeCode, boolean clearable, boolean requiresObjectId) {
        return PolicyFieldCapability.executable(changeTypeCode, clearable, requiresObjectId);
    }

    private static void validateUniqueFieldCodes(List<PolicyFieldDescriptor> fields) {
        Set<String> fieldCodes = new HashSet<>();
        for (PolicyFieldDescriptor field : fields) {
            if (field == null) {
                throw new PolicyFieldCatalogValidationException("字段目录不能包含空字段");
            }
            if (!fieldCodes.add(field.fieldCode())) {
                throw new PolicyFieldCatalogValidationException("字段目录编码重复: " + field.fieldCode());
            }
        }
    }

    private static String calculateHash(String version, List<PolicyFieldDescriptor> fields) {
        String canonical = version + "\n"
                + fields.stream().map(PolicyFieldDescriptor::canonicalForm).collect(Collectors.joining("\n"));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }
}
