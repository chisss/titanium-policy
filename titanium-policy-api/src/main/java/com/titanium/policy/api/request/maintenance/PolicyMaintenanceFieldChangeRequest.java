package com.titanium.policy.api.request.maintenance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 保全案件提交给 Policy 的单字段结构化变更。 */
@Schema(description = "Policy 保全字段变更")
public record PolicyMaintenanceFieldChangeRequest(
        @NotBlank @Size(max = 64) String itemCode,
        @NotBlank @Size(max = 128) String objectId,
        @NotBlank @Size(max = 128) String fieldCode,
        @NotBlank @Size(max = 16) String dataType,
        @Size(max = 32768) String canonicalValue) {
}
