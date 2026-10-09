package com.titanium.policy.api.response.policy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 保单贷款还款回执（跨域 Feign 出参，g04-04）。
 * <p>
 * 回执承载保单域权威的还款分配事实（利息/本金拆分、本段计息）与还款后余额，供调用方（保全域）
 * 做资金入账（支付域收款）、账务冲销（计费域）与保全留痕——消费方一律以此为准，不得复算。
 * 幂等重试时回执回显首次入账的分配结果（金额、日期、利息以首次为准）。
 * </p>
 *
 * @param repaymentId 还款业务号（幂等键）
 * @param policyId 保单ID
 * @param loanId 贷款明细业务号
 * @param repaymentAmount 本次还款总额
 * @param interestPortion 本次冲减的利息部分
 * @param principalPortion 本次冲减的本金部分
 * @param accruedInterest 本次计息区间增量利息（单利 365）
 * @param outstandingPrincipalAfter 还款后未偿本金
 * @param outstandingInterestAfter 还款后已计未付利息
 * @param settled 是否已结清
 * @param repaymentDate 还款日期
 */
@Schema(description = "保单贷款还款回执")
public record RepayPolicyLoanResponse(
        @Schema(description = "还款业务号") String repaymentId,
        @Schema(description = "保单ID") String policyId,
        @Schema(description = "贷款明细业务号") String loanId,
        @Schema(description = "本次还款总额") BigDecimal repaymentAmount,
        @Schema(description = "本次冲减的利息部分") BigDecimal interestPortion,
        @Schema(description = "本次冲减的本金部分") BigDecimal principalPortion,
        @Schema(description = "本次计息区间增量利息（单利 365）") BigDecimal accruedInterest,
        @Schema(description = "还款后未偿本金") BigDecimal outstandingPrincipalAfter,
        @Schema(description = "还款后已计未付利息") BigDecimal outstandingInterestAfter,
        @Schema(description = "是否已结清") boolean settled,
        @Schema(description = "还款日期") LocalDateTime repaymentDate) {
}
