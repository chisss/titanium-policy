package com.titanium.policy.query.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.common.jpa.BaseView;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 保单贷款明细读模型实体（CQRS 读侧，g04-03）。
 * <p>
 * 由 {@code PolicyLoanDisbursedEvent}（建行）与 {@code PolicyLoanRepaidEvent}（更新余额与结清标记，
 * g04-04）投影维护，映射 {@code t_policy_loan}（g04-02 自 maintenance 库迁入的状态存储表，
 * g04-03 升格为读模型投影表）。每笔放款一行，主键 = 贷款明细业务号 {@code loanId}（幂等 upsert），
 * 继承 {@link BaseView} 复用租户ID、投影时间与乐观锁版本等读模型公共字段。
 * </p>
 */
@Entity
@Table(name = "t_policy_loan", indexes = {
        @Index(name = "idx_policy_loan_tenant", columnList = "tenant_id"),
        @Index(name = "idx_policy_loan_policy", columnList = "policy_id")
})
@Getter
@Setter
public class PolicyLoanView extends BaseView {

    /** 主键：贷款明细业务号（loanId，调用方生成） */
    @Id
    @Column(name = "id", nullable = false, length = 32)
    private String        id;

    /** 保单ID */
    @Column(name = "policy_id", nullable = false, length = 36)
    private String        policyId;

    /** 放款本金 */
    @Column(name = "loan_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal    loanAmount;

    /** 冻结利率（放款时点取产品域贷款策略） */
    @Column(name = "interest_rate", precision = 9, scale = 6)
    private BigDecimal    interestRate;

    /** 放款日期 */
    @Column(name = "loan_date")
    private LocalDateTime loanDate;

    /** 已偿还标识（0 未偿还 / 1 已偿还） */
    @Column(name = "repaid", nullable = false)
    private Integer       repaid;

    /** 未偿本金（放款时 = 放款本金，还款按先息后本递减；结清后为 0） */
    @Column(name = "outstanding_principal", nullable = false, precision = 18, scale = 2)
    private BigDecimal    outstandingPrincipal;

    /** 已计未付利息（单利 365 分段累计，还款先冲本列） */
    @Column(name = "unpaid_interest", nullable = false, precision = 18, scale = 2)
    private BigDecimal    unpaidInterest;
}
