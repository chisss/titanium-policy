package com.titanium.policy.api.response.fieldcatalog;

import io.swagger.v3.oas.annotations.media.Schema;

/** Policy 字段能力响应。 */
@Schema(description = "Policy 字段对保全流程开放的能力")
public record PolicyFieldCapabilityResponse(
        @Schema(description = "是否允许读取") boolean readable,
        @Schema(description = "是否允许提交变更提案") boolean proposable,
        @Schema(description = "是否允许清空") boolean clearable,
        @Schema(description = "是否已具备真实执行映射") boolean executionSupported,
        @Schema(description = "是否必须指定稳定业务对象ID") boolean requiresObjectId,
        @Schema(description = "业务变更类别编码") String changeTypeCode) {
}
