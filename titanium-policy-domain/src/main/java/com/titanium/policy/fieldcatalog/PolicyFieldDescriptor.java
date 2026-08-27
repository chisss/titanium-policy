package com.titanium.policy.fieldcatalog;

import java.time.LocalDate;
import java.util.regex.Pattern;

import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldObjectType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;

/** Policy 对外发布的稳定字段描述。 */
public record PolicyFieldDescriptor(
        String fieldCode,
        PolicyFieldObjectType objectType,
        PolicyFieldValueType valueType,
        String labelKey,
        boolean collection,
        String objectIdentityField,
        PolicyFieldCapability capability,
        PolicyFieldSensitivityLevel sensitivity,
        PolicyFieldMaskingPolicy maskingPolicy,
        LocalDate deprecatedAt) {

    private static final Pattern FIELD_CODE_PATTERN =
            Pattern.compile("[a-z][a-zA-Z0-9]*(\\.[a-z][a-zA-Z0-9]*)+");

    public PolicyFieldDescriptor {
        fieldCode = requireText(fieldCode, "字段编码");
        labelKey = requireText(labelKey, "标签键");
        objectIdentityField = normalize(objectIdentityField);
        if (!FIELD_CODE_PATTERN.matcher(fieldCode).matches()) {
            throw new PolicyFieldCatalogValidationException("字段编码格式非法: " + fieldCode);
        }
        if (objectType == null || valueType == null || capability == null
                || sensitivity == null || maskingPolicy == null) {
            throw new PolicyFieldCatalogValidationException("字段类型、能力和敏感策略不能为空");
        }
        if (collection && objectIdentityField == null) {
            throw new PolicyFieldCatalogValidationException("集合字段必须配置稳定业务对象标识");
        }
        if (!collection && objectIdentityField != null) {
            throw new PolicyFieldCatalogValidationException("非集合字段不能配置业务对象标识");
        }
        if (capability.requiresObjectId() && !collection) {
            throw new PolicyFieldCatalogValidationException("仅集合字段可以要求业务对象标识");
        }
        if (sensitivity.requiresMasking() && maskingPolicy == PolicyFieldMaskingPolicy.NONE) {
            throw new PolicyFieldCatalogValidationException("敏感字段必须配置掩码策略");
        }
    }

    public static PolicyFieldDescriptor scalar(
            String fieldCode,
            PolicyFieldObjectType objectType,
            PolicyFieldValueType valueType,
            String labelKey,
            PolicyFieldCapability capability,
            PolicyFieldSensitivityLevel sensitivity,
            PolicyFieldMaskingPolicy maskingPolicy) {
        return new PolicyFieldDescriptor(fieldCode, objectType, valueType, labelKey, false, null,
                capability, sensitivity, maskingPolicy, null);
    }

    public static PolicyFieldDescriptor collectionField(
            String fieldCode,
            PolicyFieldObjectType objectType,
            PolicyFieldValueType valueType,
            String labelKey,
            String objectIdentityField,
            PolicyFieldCapability capability,
            PolicyFieldSensitivityLevel sensitivity,
            PolicyFieldMaskingPolicy maskingPolicy) {
        return new PolicyFieldDescriptor(fieldCode, objectType, valueType, labelKey, true,
                objectIdentityField, capability, sensitivity, maskingPolicy, null);
    }

    String canonicalForm() {
        return String.join("\u001f",
                fieldCode,
                objectType.getCode(),
                valueType.getCode(),
                labelKey,
                Boolean.toString(collection),
                nullToEmpty(objectIdentityField),
                Boolean.toString(capability.readable()),
                Boolean.toString(capability.proposable()),
                Boolean.toString(capability.clearable()),
                Boolean.toString(capability.executionSupported()),
                Boolean.toString(capability.requiresObjectId()),
                nullToEmpty(capability.changeTypeCode()),
                sensitivity.getCode(),
                maskingPolicy.getCode(),
                deprecatedAt == null ? "" : deprecatedAt.toString());
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new PolicyFieldCatalogValidationException(label + "不能为空");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
