package com.titanium.policy.fieldcatalog;

/** Policy 字段对保全流程开放的能力。 */
public record PolicyFieldCapability(
        boolean readable,
        boolean proposable,
        boolean clearable,
        boolean executionSupported,
        boolean requiresObjectId,
        String changeTypeCode) {

    public PolicyFieldCapability {
        changeTypeCode = normalize(changeTypeCode);
        if (clearable && !proposable) {
            throw new PolicyFieldCatalogValidationException("可清空字段必须允许提交变更提案");
        }
        if (executionSupported && !proposable) {
            throw new PolicyFieldCatalogValidationException("可执行字段必须允许提交变更提案");
        }
        if (proposable && changeTypeCode == null) {
            throw new PolicyFieldCatalogValidationException("可提案字段必须配置业务变更类别");
        }
    }

    /** 创建首期允许提案、尚未开放真实写入的字段能力。 */
    public static PolicyFieldCapability proposable(
            String changeTypeCode, boolean clearable, boolean requiresObjectId) {
        return new PolicyFieldCapability(true, true, clearable, false, requiresObjectId, changeTypeCode);
    }

    /** 创建已具备 Policy 聚合真实字段执行器的能力。 */
    public static PolicyFieldCapability executable(
            String changeTypeCode, boolean clearable, boolean requiresObjectId) {
        return new PolicyFieldCapability(true, true, clearable, true, requiresObjectId, changeTypeCode);
    }

    /** 创建只读字段能力。 */
    public static PolicyFieldCapability readOnly() {
        return new PolicyFieldCapability(true, false, false, false, false, null);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
