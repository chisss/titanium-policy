package com.titanium.policy.command;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.policy.aggregate.PolicyLoan;


/**
 * 保单贷款放款命令（g04-03，record）。
 * <p>
 * 由保全域放款编排器经 {@code PolicyLoanApi} 跨域派发：贷款业务号 {@code loanId}、放款金额、
 * 放款时冻结的利率与放款时点均由调用方（保全域）承载——冻结利率取自产品域已发布贷款策略，
 * 放款日期为实际放款时点，本命令仅在命令聚合外生成、聚合并入账务时原样冻结。
 * </p>
 * <p>
 * 🔴 路由标识为 {@code loanAccountId}（{@code "LOAN:" + policyId}）：贷款账户聚合与保单聚合
 * 标识空间隔离，不得用 {@code policyId} 直接路由。调用方一律经 {@link #of} 派生构造，
 * 禁止自行拼接前缀。
 * </p>
 *
 * @param tenantId 租户ID
 * @param loanAccountId 贷款账户聚合标识（"LOAN:" + policyId）
 * @param policyId 保单ID（业务字段，贷款账户与保单 1:1）
 * @param loanId 贷款明细业务号（调用方生成，全局唯一，幂等键）
 * @param loanAmount 放款本金（> 0）
 * @param interestRate 冻结利率（[0,1]，放款时点取产品域贷款策略）
 * @param loanDate 放款日期
 * @param operatorId 操作人ID
 */
public record DisbursePolicyLoanCommand(
        String tenantId,
        @TargetAggregateIdentifier String loanAccountId,
        String policyId,
        String loanId,
        BigDecimal loanAmount,
        BigDecimal interestRate,
        LocalDateTime loanDate,
        String operatorId) {

    /**
     * 派生构造：贷款账户聚合标识由保单ID统一派生（{@link PolicyLoan#loanAccountIdOf}），
     * 调用方只传保单ID，不接触标识拼接。
     */
    public static DisbursePolicyLoanCommand of(
            String tenantId, String policyId, String loanId, BigDecimal loanAmount,
            BigDecimal interestRate, LocalDateTime loanDate, String operatorId) {
        return new DisbursePolicyLoanCommand(tenantId, PolicyLoan.loanAccountIdOf(policyId), policyId,
                loanId, loanAmount, interestRate, loanDate, operatorId);
    }
}
