package com.titanium.policy.api.request.policy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 保单贷款还款请求（跨域 Feign 入参，g04-04）。
 * <p>
 * 调用方（保全域还款编排器）承载还款业务号与还款要素。🔴 <b>利息/本金拆分不进请求</b>——
 * 「先息后本」分配依赖贷款明细的未偿状态与计息区间，是保单域贷款账户聚合的现场计算职责
 * （见 {@code PolicyLoan#repay}），调用方从回执读取分配结果，不得自行复算。
 * </p>
 * <p>
 * 操作人经 {@code X-Operator-Id} 请求头传递，不进请求体（与放款请求同一惯例）。
 * </p>
 */
@Schema(description = "保单贷款还款请求")
public record RepayPolicyLoanRequest(
        @NotBlank @Size(max = 32) @Schema(description = "还款业务号（调用方生成，全局唯一幂等键）", example = "REPAY-2026-0001")
        String repaymentId,

        @NotBlank @Size(max = 36) @Schema(description = "保单ID", example = "POL-2026-0001")
        String policyId,

        @NotBlank @Size(max = 32) @Schema(description = "贷款明细业务号（目标明细）", example = "LOAN-2026-0001")
        String loanId,

        @NotNull @Positive @Schema(description = "本次还款总额（不得超过欠款总额 = 未偿本金 + 已计未付利息）",
                example = "911.10")
        BigDecimal repaymentAmount,

        @NotNull @Schema(description = "还款日期（本次计息区间终点，不得早于上次计息日）")
        LocalDateTime repaymentDate) {
}
