package com.titanium.policy.valueobject.maintenance;

/** Policy 字段执行器返回的实际合同字段值。 */
public record PolicyMaintenanceAppliedField(
        String itemCode,
        String objectId,
        String fieldCode,
        String dataType,
        String canonicalValue) {
}
