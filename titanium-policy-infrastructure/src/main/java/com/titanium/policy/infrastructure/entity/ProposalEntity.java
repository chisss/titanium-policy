package com.titanium.policy.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 投保意向单数据库实体
 * <p>
 * 对应t_proposal表，存储投保意向单的基本信息
 * </p>
 */
@Entity
@Table(name = "t_proposal")
@Getter
@Setter
public class ProposalEntity {
    /**
     * 意向单ID
     */
    @Id
    @Column(name = "proposal_id")
    private String proposalId;

    /**
     * 意向单编号
     */
    @Column(name = "proposal_no", unique = true, nullable = false)
    private String proposalNo;

    /**
     * 保单形态：个单/团单/父子
     */
    @Column(name = "policy_form", nullable = false)
    private String policyForm;

    /**
     * 父意向单ID
     */
    @Column(name = "parent_proposal_id")
    private String parentProposalId;

    /**
     * 销售渠道
     */
    @Column(name = "channel", nullable = false)
    private String channel;

    /**
     * 客户ID
     */
    @Column(name = "customer_id", nullable = false)
    private String customerId;

    /**
     * 意向保额
     */
    @Column(name = "intended_sum_insured", nullable = false)
    private double intendedSumInsured;

    /**
     * 意向保费
     */
    @Column(name = "intended_premium", nullable = false)
    private double intendedPremium;

    /**
     * 币种
     */
    @Column(name = "currency", nullable = false)
    private String currency;

    /**
     * 保障期限起期
     */
    @Column(name = "insurance_period_start", nullable = false)
    private LocalDateTime insurancePeriodStart;

    /**
     * 保障期限止期
     */
    @Column(name = "insurance_period_end", nullable = false)
    private LocalDateTime insurancePeriodEnd;

    /**
     * 意向险种编码
     */
    @Column(name = "expected_product_code", nullable = false)
    private String expectedProductCode;

    /**
     * 状态编码
     */
    @Column(name = "status_code", nullable = false)
    private String statusCode;

    /**
     * 状态变更时间
     */
    @Column(name = "status_time", nullable = false)
    private LocalDateTime statusTime;

    /**
     * 变更原因
     */
    @Column(name = "change_reason")
    private String changeReason;

    /**
     * 创建时间
     */
    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    /**
     * 租户ID
     */
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;
}