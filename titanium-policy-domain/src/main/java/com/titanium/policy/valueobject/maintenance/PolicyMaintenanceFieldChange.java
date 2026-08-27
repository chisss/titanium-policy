package com.titanium.policy.valueobject.maintenance;

import com.titanium.policy.exception.PolicyBusinessRuleException;

/** Policy 聚合待执行的规范化字段变更。 */
public record PolicyMaintenanceFieldChange(
        String itemCode,
        String objectId,
        String fieldCode,
        String dataType,
        String canonicalValue) {

    public PolicyMaintenanceFieldChange {
        itemCode = requireText(itemCode, "保全项编码不能为空");
        objectId = requireText(objectId, "字段对象标识不能为空");
        fieldCode = requireText(fieldCode, "字段编码不能为空");
        dataType = requireText(dataType, "字段类型不能为空").toUpperCase();
    }

    public String key() {
        return objectId + ":" + fieldCode;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new PolicyBusinessRuleException("POLICY_MAINTENANCE_REQUEST_INVALID", message);
        }
        return value.trim();
    }
}
