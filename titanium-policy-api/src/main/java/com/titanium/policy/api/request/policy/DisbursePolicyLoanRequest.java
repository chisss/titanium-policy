package com.titanium.policy.api.request.policy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 保单贷款放款请求（跨域 Feign 入参，g04-03）。
 * <p>
 * 调用方（保全域放款编排器）承载贷款业务号与放款要素：冻结利率取自产品域已发布贷款策略，
 * 放款日期为实际放款时点。操作人经 {@code X-Operator-Id} 请求头传递，不进请求体
 * （与 {@code ApplyPolicyMaintenanceRequest} 同一惯例）。
 * </p>
 */
@Schema(description = "保单贷款放款请求")
public record DisbursePolicyLoanRequest(
        @NotBlank @Size(max = 32) @Schema(description = "贷款明细业务号（调用方生成，全局唯一幂等键）", example = "LOAN-2026-0001")
        String loanId,

        @NotBlank @Size(max = 36) @Schema(description = "保单ID", example = "POL-2026-0001")
        String policyId,

        @NotNull @Positive @Schema(description = "放款本金（> 0）", example = "50000.00")
        BigDecimal loanAmount,

        @NotNull @DecimalMin("0") @DecimalMax("1") @Schema(description = "冻结利率（[0,1]，放款时点取产品域贷款策略）", example = "0.05")
        BigDecimal interestRate,

        @NotNull @Schema(description = "放款日期（实际放款时点）")
        LocalDateTime loanDate) {
}
