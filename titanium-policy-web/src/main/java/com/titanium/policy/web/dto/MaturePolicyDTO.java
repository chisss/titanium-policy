package com.titanium.policy.web.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 满期给付请求（两全险/生存给付型寿险）
 * <p>
 * 承载满期给付金额，经 {@code PolicyWebMapper} 转换为 {@code MaturePolicyCommand}，操作人/租户ID 取请求头。
 * </p>
 */
@Schema(description = "满期给付请求")
@Data
public class MaturePolicyDTO {

    @Schema(description = "满期给付金额（满期生存保险金）", example = "100000.00")
    private BigDecimal maturityBenefit;
}
