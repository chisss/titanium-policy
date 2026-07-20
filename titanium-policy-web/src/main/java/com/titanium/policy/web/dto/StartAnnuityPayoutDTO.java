package com.titanium.policy.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.policy.common.enums.AnnuityPayoutFrequency;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 启动年金给付请求
 * <p>
 * 承载给付起始日、频率、每期给付金额与总期数，经 {@code PolicyWebMapper} 转换为
 * {@code StartAnnuityPayoutCommand}（金额+币种组装为 {@code Money}），操作人/租户ID 取请求头。
 * </p>
 */
@Schema(description = "启动年金给付请求")
@Data
public class StartAnnuityPayoutDTO {

    @Schema(description = "给付起始日", example = "2026-08-01T00:00:00")
    private LocalDateTime          startDate;

    @Schema(description = "给付频率", example = "ANNUALLY")
    private AnnuityPayoutFrequency frequency;

    @Schema(description = "每期给付金额", example = "12000.00")
    private BigDecimal             amountPerInstallment;

    @Schema(description = "币种", example = "CNY")
    private String                 currency;

    @Schema(description = "总给付期数（null 表示终身年金）", example = "20")
    private Integer                totalInstallments;
}
