package com.titanium.policy.api.response.fieldcatalog;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Policy 字段目录响应（业务时点快照）。
 *
 * <p>🔴 {@code fallback} 必须由消费方<strong>显式判读</strong>：为 {@code true} 时表示
 * 「请求的业务日期早于目录最早版本」，返回的是**最早版本的内容**而非该时点的真实版本——
 * 这是有意的降级（不报错），但不得静默：消费方须据此把该次取数标记为降级口径，
 * 而不是当成正常命中。语义定义见 {@code PolicyFieldCatalogPublication#resolveAt}。</p>
 */
@Schema(description = "Policy 权威字段目录")
public record PolicyFieldCatalogResponse(
        @Schema(description = "租户ID") String tenantId,
        @Schema(description = "产品类型筛选") String productType,
        @Schema(description = "保单类型筛选") String policyType,
        @Schema(description = "目录解析业务日期") LocalDate businessDate,
        @Schema(description = "是否降级：业务日期早于最早目录版本，返回的是最早版本而非该时点真实版本")
                boolean fallback,
        @Schema(description = "目录版本") String catalogVersion,
        @Schema(description = "目录内容SHA-256") String contentHash,
        @Schema(description = "字段列表") List<PolicyFieldDescriptorResponse> fields) {

    public PolicyFieldCatalogResponse {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
