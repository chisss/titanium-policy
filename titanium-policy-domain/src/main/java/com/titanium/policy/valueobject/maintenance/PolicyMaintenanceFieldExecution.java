package com.titanium.policy.valueobject.maintenance;

/** 单个字段执行器产生的新合同状态及实际值。 */
public record PolicyMaintenanceFieldExecution(
        PolicyMaintenanceExecutionState state,
        PolicyMaintenanceAppliedField appliedField) {
}
