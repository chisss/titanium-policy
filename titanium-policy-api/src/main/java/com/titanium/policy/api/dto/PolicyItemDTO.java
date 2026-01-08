package com.titanium.policy.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "保单项DTO")
@Data
public class PolicyItemDTO {
    @Schema(description = "保单项ID", example = "item-001")
    private String    itemId;
    @Schema(description = "保障ID", example = "coverage-001")
    private String    coverageId;
    @Schema(description = "保障名称", example = "基本保障")
    private String    coverageName;
    @Schema(description = "保额", example = "1000000.00")
    private AmountDTO sumInsured;
    @Schema(description = "保费", example = "1000.00")
    private AmountDTO premium;
    @Schema(description = "保单项描述", example = "基本保障保额1000000.00，保费1000.00")
    private String    description;
}
