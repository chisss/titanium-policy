package com.titanium.policy.api.dto;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "创建保单DTO")
@Data
public class CreatePolicyDTO {
    @Schema(description = "保单ID", example = "policy-001")
    private String              policyId;

    @Schema(description = "保单编号", example = "POL-2024-0001")
    private String              policyNumber;

    @Schema(description = "客户ID", example = "customer-001")
    private String              customerId;

    @Schema(description = "产品ID", example = "product-001")
    private String              productId;

    @Schema(description = "生效日期", example = "2024-01-01T00:00:00")
    private LocalDateTime       effectiveDate;

    @Schema(description = "过期日期", example = "2024-12-31T23:59:59")
    private LocalDateTime       expiryDate;

    @Schema(description = "保费")
    private AmountDTO           premium;

    @Schema(description = "保单项列表")
    private List<PolicyItemDTO> policyItems;

    @Schema(description = "租户ID", example = "tenant-001")
    private String              tenantId;
}
