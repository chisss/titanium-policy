package com.titanium.policy.api.response.fieldcatalog;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Policy 字段目录响应。 */
@Schema(description = "Policy 权威字段目录")
public record PolicyFieldCatalogResponse(
        @Schema(description = "租户ID") String tenantId,
        @Schema(description = "产品类型筛选") String productType,
        @Schema(description = "保单类型筛选") String policyType,
        @Schema(description = "目录解析业务日期") LocalDate businessDate,
        @Schema(description = "目录版本") String catalogVersion,
        @Schema(description = "目录内容SHA-256") String contentHash,
        @Schema(description = "字段列表") List<PolicyFieldDescriptorResponse> fields) {

    public PolicyFieldCatalogResponse {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
