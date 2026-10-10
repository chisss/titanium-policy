package com.titanium.policy.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 保单贷款放款事件（g04-03，record）。
 * <p>
 * 放款为资金事实：本金与放款时冻结的利率一经入账不再变更，还款（g04-04）仅摊销未偿本金，
 * 不回溯本事件。事件由 {@code PolicyLoanDisbursedEvent} 投影维护 {@code t_policy_loan} 读模型。
 * </p>
 * <p>
 * 🔴 贷款账户聚合标识为 {@code loanAccountId = "LOAN:" + policyId}，与保单聚合标识
 * （{@code policyId}）隔离——两聚合若共用标识，JPA 事件存储按标识返回<b>全部</b>事件
 * （不按类型过滤），PolicyLoan 回放 Policy 事件流会导致聚合标识落位失败。
 * </p>
 *
 * @param loanAccountId 贷款账户聚合标识（"LOAN:" + policyId，与保单聚合标识空间隔离）
 * @param loanId 贷款明细业务号
 * @param policyId 保单ID
 * @param loanAmount 放款本金
 * @param interestRate 冻结利率
 * @param loanDate 放款日期
 * @param operatorId 操作人ID
 * @param tenantId 租户ID
 */
public record PolicyLoanDisbursedEvent(
        String loanAccountId,
        String loanId,
        String policyId,
        BigDecimal loanAmount,
        BigDecimal interestRate,
        LocalDateTime loanDate,
        String operatorId,
        String tenantId) {
}
