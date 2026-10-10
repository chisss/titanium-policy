package com.titanium.policy.entity.loan;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 保单贷款还款记录（贷款明细内嵌事实，g04-04）。
 * <p>
 * 每次还款一条：{@code repaymentId} 由调用方（保全域）稳定生成、全局唯一，是本条还款的幂等键
 * （三连写部分失败后重试时同键同额幂等回放，同键异额显式拒绝）。金额拆分（利息/本金）由贷款账户
 * 聚合按「先息后本」在现场计算后冻结进本记录——{@code accruedInterest} 是本次还款所覆盖计息区间
 * （上次计息日 → 本次还款日）内按单利 365 计提的利息，与保全域
 * {@code PolicyLoanDetail.accruedInterest(days)} 同口径（首个计息区间为起贷日 → 还款日，逐位一致）。
 * </p>
 *
 * @param repaymentId 还款业务号（调用方生成，全局唯一，幂等键）
 * @param repaymentAmount 本次还款总额（> 0）
 * @param interestPortion 本次冲减的利息部分（先息后本之「先息」）
 * @param principalPortion 本次冲减的本金部分（先息后本之「后本」，可为 0）
 * @param accruedInterest 本次计息区间内计提的利息（单利 365，段增量）
 * @param repaymentDate 还款日期（本次计息区间的终点，亦为下次计息区间的起点）
 */
public record PolicyLoanRepaymentRecord(
        String repaymentId,
        BigDecimal repaymentAmount,
        BigDecimal interestPortion,
        BigDecimal principalPortion,
        BigDecimal accruedInterest,
        LocalDateTime repaymentDate) {
}
