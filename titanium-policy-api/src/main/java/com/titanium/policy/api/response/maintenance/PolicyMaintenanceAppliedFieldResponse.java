package com.titanium.policy.api.response.maintenance;

import io.swagger.v3.oas.annotations.media.Schema;

/** Policy 实际应用后的单字段权威值。 */
@Schema(description = "Policy 保全实际字段值")
public record PolicyMaintenanceAppliedFieldResponse(
        String itemCode,
        String objectId,
        String fieldCode,
        String dataType,
        String canonicalValue) {
}
