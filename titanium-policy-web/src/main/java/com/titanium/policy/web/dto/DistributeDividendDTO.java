package com.titanium.policy.web.dto;

import java.math.BigDecimal;

import com.titanium.policy.common.enums.DividendOption;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 红利派发请求
 * <p>
 * 承载本次派发红利金额、领取方式与保单年度，经 {@code PolicyWebMapper} 转换为
 * {@code DistributeDividendCommand}，操作人/租户ID 取请求头。
 * </p>
 */
@Schema(description = "红利派发请求")
@Data
public class DistributeDividendDTO {

    @Schema(description = "本次派发红利金额", example = "1500.00")
    private BigDecimal     dividendAmount;

    @Schema(description = "红利领取方式", example = "ACCUMULATE")
    private DividendOption option;

    @Schema(description = "保单年度（第几个保单年度）", example = "3")
    private int            policyYear;
}
