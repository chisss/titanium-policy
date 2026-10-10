package com.titanium.policy.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 保单贷款还款事件（g04-04，record）。
 * <p>
 * 还款为资金事实：金额拆分（{@code interestPortion} / {@code principalPortion}）与计息区间增量
 * （{@code accruedInterest}）由贷款账户聚合在本次还款现场按「先息后本 + 单利 365」计算后冻结进
 * 事件——事件即权威分配，消费方（读模型投影、保全留痕、计费冲销）一律以此为准，不得复算。
 * </p>
 * <p>
 * 🔴 {@code outstandingPrincipalAfter} / {@code outstandingInterestAfter} 是本次还款后的余额快照
 * （派生自全量还款记录，写入事件供读模型直接落列，免去读侧重算）；{@code settled} 为结清标记，
 * 是保全域触发合同要素回写与留痕的判据。聚合标识 {@code loanAccountId = "LOAN:" + policyId}，
 * 与保单聚合标识空间隔离（见 {@link PolicyLoanDisbursedEvent}）。
 * </p>
 *
 * @param loanAccountId 贷款账户聚合标识（"LOAN:" + policyId）
 * @param policyId 保单ID
 * @param loanId 贷款明细业务号
 * @param repaymentId 还款业务号（调用方生成，全局唯一幂等键）
 * @param repaymentAmount 本次还款总额
 * @param interestPortion 本次冲减的利息部分
 * @param principalPortion 本次冲减的本金部分
 * @param accruedInterest 本次计息区间增量利息（单利 365）
 * @param outstandingPrincipalAfter 还款后未偿本金
 * @param outstandingInterestAfter 还款后已计未付利息
 * @param settled 是否已结清
 * @param repaymentDate 还款日期
 * @param operatorId 操作人ID
 * @param tenantId 租户ID
 */
public record PolicyLoanRepaidEvent(
        String loanAccountId,
        String policyId,
        String loanId,
        String repaymentId,
        BigDecimal repaymentAmount,
        BigDecimal interestPortion,
        BigDecimal principalPortion,
        BigDecimal accruedInterest,
        BigDecimal outstandingPrincipalAfter,
        BigDecimal outstandingInterestAfter,
        boolean settled,
        LocalDateTime repaymentDate,
        String operatorId,
        String tenantId) {
}
