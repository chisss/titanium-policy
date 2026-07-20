package com.titanium.policy.api.response;

import java.time.LocalDateTime;
import java.util.List;

import com.titanium.metadata.enums.policy.PolicyEnum;
import com.titanium.policy.api.model.Amount;
import com.titanium.policy.api.model.PolicyItem;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "保单DTO")
@Data
public class PolicyResponse {
    @Schema(description = "保单ID", example = "1234567890")
    private String                  policyId;
    @Schema(description = "保单号", example = "P20230801001")
    private String                  policyNo;
    @Schema(description = "客户ID", example = "C20230801001")
    private String                  customerId;
    @Schema(description = "产品ID", example = "P20230801001")
    private String                  productId;
    @Schema(description = "生效日期", example = "2023-08-01T00:00:00")
    private LocalDateTime           effectiveDate;
    @Schema(description = "过期日期", example = "2023-12-31T23:59:59")
    private LocalDateTime           expiryDate;
    @Schema(description = "保费", example = "1000.00")
    private Amount               premium;
    @Schema(description = "保单状态", example = "EFFECTIVE", exampleClasses = PolicyEnum.PolicyStatus.class)
    private PolicyEnum.PolicyStatus status;
    @Schema(description = "保单项列表")
    private List<PolicyItem>     policyItems;
    @Schema(description = "创建时间", example = "2023-08-01T00:00:00")
    private LocalDateTime           createdAt;
    @Schema(description = "更新时间", example = "2023-08-01T00:00:00")
    private LocalDateTime           updatedAt;
    @Schema(description = "租户ID", example = "tenant-001")
    private String                  tenantId;

}
