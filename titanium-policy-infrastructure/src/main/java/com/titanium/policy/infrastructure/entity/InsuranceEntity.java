package com.titanium.policy.infrastructure.entity;

import java.time.LocalDateTime;

import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;
import com.titanium.policy.valueobject.insurance.InsuranceStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 投保单数据库实体
 * <p>
 * 对应t_insurance_application表，存储投保单的基本信息
 * </p>
 */
@Entity
@Table(name = "t_insurance")
@Getter
@Setter
public class InsuranceEntity {
    /**
     * 投保单ID
     */
    @Id
    @Column(name = "insurance_id")
    private String        insuranceId;

    /**
     * 投保单编号
     */
    @Column(name = "insurance_no", unique = true, nullable = false)
    private String        insuranceNo;

    /**
     * 关联意向单ID
     */
    @Column(name = "proposal_id")
    private String        proposalId;

    /**
     * 保单形态：个单/团单/父子
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "policy_form", nullable = false)
    private PolicyForm    policyForm;

    /**
     * 父投保单ID
     */
    @Column(name = "parent_insurance_id")
    private String        parentInsuranceId;

    /**
     * 投保人ID
     */
    @Column(name = "holder_id", nullable = false)
    private String        holderId;

    /**
     * 被保险人数量
     */
    @Column(name = "insured_count", nullable = false)
    private int           insuredCount;

    /**
     * 精确保费
     */
    @Column(name = "exact_premium", nullable = false)
    private double        exactPremium;

    /**
     * 币种
     */
    @Column(name = "currency", nullable = false)
    private String        currency;

    /**
     * 精确保障期限起期
     */
    @Column(name = "insurance_period_start", nullable = false)
    private LocalDateTime insurancePeriodStart;

    /**
     * 精确保障期限止期
     */
    @Column(name = "insurance_period_end", nullable = false)
    private LocalDateTime insurancePeriodEnd;

    /**
     * 核保优先级
     */
    @Column(name = "underwriting_priority", nullable = false)
    private int           underwritingPriority;

    /**
     * 核保单号
     */
    @Column(name = "underwriting_id")
    private String        underwritingId;

    /**
     * 核保结果编码
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "underwriting_result_code")
    private ConclusionType underwritingResultCode;

    /**
     * 核保意见
     */
    @Column(name = "underwriting_opinion", length = 500)
    private String        underwritingOpinion;

    /**
     * 核保人ID
     */
    @Column(name = "underwriter_id")
    private String        underwriterId;

    /**
     * 核保时间
     */
    @Column(name = "underwriting_time")
    private LocalDateTime underwritingTime;

    /**
     * 承保条件
     */
    @Column(name = "underwriting_condition")
    private String        underwritingCondition;

    /**
     * 状态编码
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_code", nullable = false)
    private InsuranceStatus.StatusCode statusCode;

    /**
     * 状态变更时间
     */
    @Column(name = "status_time", nullable = false)
    private LocalDateTime statusTime;

    /**
     * 变更原因
     */
    @Column(name = "change_reason")
    private String        changeReason;

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
    private String        tenantId;
}
