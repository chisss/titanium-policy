package com.titanium.policy.command;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.policy.aggregate.PolicyLoan;

/**
 * 保单贷款还款命令（g04-04，record）。
 * <p>
 * 由保全域还款编排器经 {@code PolicyLoanApi} 跨域派发：还款业务号 {@code repaymentId} 由调用方
 * 稳定生成（全局唯一幂等键），还款总额与还款日期由保全受理事实承载。🔴 金额拆分（利息 / 本金）
 * <b>不由本命令携带</b>——「先息后本」分配依赖贷款明细的未偿状态与计息区间，是贷款账户聚合的
 * 现场计算职责（持状态者计算，避免调用方复算漂移）；调用方可从命令返回值读到本次分配结果。
 * </p>
 * <p>
 * 🔴 路由标识为 {@code loanAccountId}（{@code "LOAN:" + policyId}），与放款命令同规：贷款账户
 * 聚合与保单聚合标识空间隔离。调用方一律经 {@link #of} 派生构造，禁止自行拼接前缀。
 * </p>
 *
 * @param tenantId 租户ID
 * @param loanAccountId 贷款账户聚合标识（"LOAN:" + policyId）
 * @param policyId 保单ID（业务字段，贷款账户与保单 1:1）
 * @param loanId 贷款明细业务号（目标明细）
 * @param repaymentId 还款业务号（调用方生成，全局唯一，幂等键）
 * @param repaymentAmount 本次还款总额（> 0；不得超过欠款总额 = 未偿本金 + 已计未付利息）
 * @param repaymentDate 还款日期（本次计息区间终点）
 * @param operatorId 操作人ID
 */
public record RepayPolicyLoanCommand(
        String tenantId,
        @TargetAggregateIdentifier String loanAccountId,
        String policyId,
        String loanId,
        String repaymentId,
        BigDecimal repaymentAmount,
        LocalDateTime repaymentDate,
        String operatorId) {

    /**
     * 派生构造：贷款账户聚合标识由保单ID统一派生（{@link PolicyLoan#loanAccountIdOf}），
     * 调用方只传保单ID，不接触标识拼接。
     */
    public static RepayPolicyLoanCommand of(
            String tenantId, String policyId, String loanId, String repaymentId,
            BigDecimal repaymentAmount, LocalDateTime repaymentDate, String operatorId) {
        return new RepayPolicyLoanCommand(tenantId, PolicyLoan.loanAccountIdOf(policyId), policyId,
                loanId, repaymentId, repaymentAmount, repaymentDate, operatorId);
    }
}
