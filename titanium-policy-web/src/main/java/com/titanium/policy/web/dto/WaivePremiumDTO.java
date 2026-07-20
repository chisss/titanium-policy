package com.titanium.policy.web.dto;

import com.titanium.policy.common.enums.PremiumWaiverReason;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 保费豁免请求
 * <p>
 * 承载豁免原因，经 {@code PolicyWebMapper} 转换为 {@code WaivePremiumCommand}，操作人/租户ID 取请求头。
 * </p>
 */
@Schema(description = "保费豁免请求")
@Data
public class WaivePremiumDTO {

    @Schema(description = "豁免原因（投保人身故/全残/重疾）", example = "TOTAL_DISABILITY")
    private PremiumWaiverReason reason;
}
