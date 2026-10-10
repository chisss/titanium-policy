package com.titanium.policy.entity.loan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.policy.exception.PolicyBusinessRuleException;

/**
 * 保单贷款明细（贷款账户聚合内实体，g04-03；g04-04 支持部分还款）。
 * <p>
 * 每笔放款一条明细：本金与冻结利率是放款时点的资金事实，不可变；还款以
 * {@link PolicyLoanRepaymentRecord} 追加记录承载（部分还款 / 提前清偿 / 结清共用同一形态），
 * 未偿本金与已计未付利息<b>由记录派生</b>而非另存可变状态——同一事实只有一个来源，事件重放天然幂等。
 * </p>
 * <p>
 * 🔴 <b>计息口径（单利 365）</b>：利息 = 计息基数 × 冻结利率 × 计息天数 / 365（scale 2 HALF_UP）。
 * 计息按区间分段累计（{@link #lastAccrualDate()} → 目标时点），基数恒为该区间起点时的<b>未偿本金</b>——
 * 部分还款冲减本金后，后续区间自动按新基数计息，不重复计已偿部分。首个计息区间（放款日 → 首次还款日）
 * 基数为原始放款本金，与保全域 {@code PolicyLoanDetail.accruedInterest(days)} 逐位一致（g04-04 / AC-05）。
 * </p>
 *
 * @param loanId 贷款明细业务号（全局唯一，幂等键）
 * @param loanAmount 放款本金
 * @param interestRate 冻结利率（[0,1]）
 * @param loanDate 放款日期
 * @param repayments 还款记录（按发生顺序追加；结清后仍保留全量历史）
 */
public record PolicyLoanEntry(
        String loanId,
        BigDecimal loanAmount,
        BigDecimal interestRate,
        LocalDateTime loanDate,
        List<PolicyLoanRepaymentRecord> repayments) {

    /** 单利计息年基准天数（与保全域 {@code PolicyLoanDetail.accruedInterest} 同口径） */
    public static final long INTEREST_BASIS_DAYS = 365;

    public PolicyLoanEntry {
        repayments = repayments == null ? List.of() : List.copyOf(repayments);
    }

    /** 已偿本金合计（各次还款本金部分之和）。 */
    public BigDecimal repaidPrincipal() {
        BigDecimal sum = BigDecimal.ZERO;
        for (PolicyLoanRepaymentRecord record : repayments) {
            sum = sum.add(record.principalPortion());
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    /** 已付利息合计（各次还款利息部分之和）。 */
    public BigDecimal paidInterest() {
        BigDecimal sum = BigDecimal.ZERO;
        for (PolicyLoanRepaymentRecord record : repayments) {
            sum = sum.add(record.interestPortion());
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    /** 累计计提利息（各计息区间增量之和）。 */
    public BigDecimal accruedInterestTotal() {
        BigDecimal sum = BigDecimal.ZERO;
        for (PolicyLoanRepaymentRecord record : repayments) {
            sum = sum.add(record.accruedInterest());
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 已计未付利息 = 累计计提利息 − 已付利息。
     * <p>
     * 先息后本分配保证 {@code interestPortion ≤ 当期未付利息}，故本值恒不为负。
     * </p>
     */
    public BigDecimal unpaidInterest() {
        return accruedInterestTotal().subtract(paidInterest()).setScale(2, RoundingMode.HALF_UP);
    }

    /** 未偿本金 = 放款本金 − 已偿本金（不为负；结清时为 0）。 */
    public BigDecimal outstandingPrincipal() {
        return loanAmount.subtract(repaidPrincipal()).setScale(2, RoundingMode.HALF_UP);
    }

    /** 上次计息截止日：无还款时为放款日，否则为最近一次还款日（后继计息区间的起点）。 */
    public LocalDateTime lastAccrualDate() {
        return repayments.isEmpty() ? loanDate : repayments.getLast().repaymentDate();
    }

    /** 是否已结清（未偿本金归零；利息随本金同步冲尽，见聚合还款分配）。 */
    public boolean settled() {
        return outstandingPrincipal().signum() == 0;
    }

    /** 按还款业务号查已有还款记录（幂等重试判定）；未命中返回 {@code null}。 */
    public PolicyLoanRepaymentRecord findRepayment(String repaymentId) {
        for (PolicyLoanRepaymentRecord record : repayments) {
            if (record.repaymentId().equals(repaymentId)) {
                return record;
            }
        }
        return null;
    }

    /**
     * 计息：上次计息日 → 给定时点区间内按未偿本金单利计提的利息（段增量）。
     * <p>
     * 首个计息区间（放款日 → 首次还款日、基数为原始本金）即 g04-04 / AC-05 的比对基准：
     * 与保全域 {@code PolicyLoanDetail.accruedInterest(days)}（{@code days} = 还款日 − 起贷日自然日）
     * 逐位一致（同公式、同 scale、同舍入）。
     * </p>
     *
     * @param at 计息截止时点（还款日）
     * @return 区间增量利息（非正天数返回 0）
     */
    public BigDecimal accruedInterestTo(LocalDateTime at) {
        if (interestRate == null || lastAccrualDate() == null) {
            throw new PolicyBusinessRuleException(PolicyErrorCode.POLICY_FINANCIAL_DATA_MISSING,
                    "贷款计息要素缺失(冻结利率或放款日期为空), loanId=" + loanId);
        }
        long days = ChronoUnit.DAYS.between(lastAccrualDate().toLocalDate(), at.toLocalDate());
        if (days <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return outstandingPrincipal().multiply(interestRate)
                .multiply(BigDecimal.valueOf(days))
                .divide(BigDecimal.valueOf(INTEREST_BASIS_DAYS), 2, RoundingMode.HALF_UP);
    }

    /**
     * 截至给定时点的未偿利息 = 已计未付利息 + 当前区间（上次计息日 → 时点）增量。
     * <p>
     * 「未偿本息」取数契约的唯一口径（{@code PolicyLoanAccount.outstandingInterest}）：调用方
     * <b>不得</b>另按「放款日 → 今日」自行计息——那会忽略部分还款已冲减的计息基数与已计提区间。
     * </p>
     *
     * @param at 估值时点
     * @return 未偿利息（含已计未付与当前区间增量）
     */
    public BigDecimal outstandingInterestAt(LocalDateTime at) {
        return unpaidInterest().add(accruedInterestTo(at)).setScale(2, RoundingMode.HALF_UP);
    }

    /** 追加一条还款记录后的新明细（不可变 record 的「后置状态」，供聚合返回与事件回放共用）。 */
    public PolicyLoanEntry withRepayment(PolicyLoanRepaymentRecord record) {
        List<PolicyLoanRepaymentRecord> appended = new ArrayList<>(repayments);
        appended.add(record);
        return new PolicyLoanEntry(loanId, loanAmount, interestRate, loanDate, appended);
    }
}
