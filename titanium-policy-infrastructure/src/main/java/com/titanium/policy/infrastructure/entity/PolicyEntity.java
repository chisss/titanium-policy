package com.titanium.policy.infrastructure.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.common.jpa.BaseEntity;
import com.titanium.metadata.enums.InsuranceType;
import com.titanium.metadata.enums.policy.PolicyEnum;
import com.titanium.metadata.enums.policy.PolicyForm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 正式保单数据库实体
 * <p>
 * 对应t_policy表，存储正式保单的基本信息。 继承 {@link BaseEntity}，复用租户ID、创建/更新时间、创建/更新人、逻辑删除等公共审计字段。
 * </p>
 */
@Entity
@Table(name = "t_policy")
@Getter
@Setter
public class PolicyEntity extends BaseEntity {
    /**
     * 保单ID
     */
    @Id
    @Column(name = "policy_id", nullable = false, length = 32)
    private String        policyId;

    /**
     * 关联投保单ID
     */
    @Column(name = "insurance_id", nullable = false, length = 32)
    private String        insuranceId;

    /**
     * 保单号
     */
    @Column(name = "policy_no", nullable = false, length = 32, unique = true)
    private String        policyNo;

    /**
     * 保单形态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "policy_form", nullable = false, length = 32)
    private PolicyForm    policyForm;

    /**
     * 父保单ID
     */
    @Column(name = "parent_policy_id", length = 32)
    private String        parentPolicyId;

    /**
     * 签发机构
     */
    @Column(name = "issue_org", nullable = false, length = 64)
    private String        issueOrg;

    /**
     * 保单状态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "policy_status", nullable = false, length = 32)
    private PolicyEnum.PolicyStatus policyStatus;

    /**
     * 保单类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "policy_type", nullable = false, length = 32)
    private PolicyForm    policyType;

    /**
     * 保险类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "insurance_type", nullable = false, length = 32)
    private InsuranceType insuranceType;

    /**
     * 投保人ID
     */
    @Column(name = "policy_holder_id", nullable = false, length = 32)
    private String        policyHolderId;

    /**
     * 被保险人ID
     */
    @Column(name = "insured_id", nullable = false, length = 32)
    private String        insuredId;

    /**
     * 保额
     */
    @Column(name = "sum_insured", nullable = false, precision = 18, scale = 2)
    private BigDecimal    sumInsured;

    /**
     * 保费
     */
    @Column(name = "premium", nullable = false, precision = 18, scale = 2)
    private BigDecimal    premium;

    /**
     * 免赔额
     */
    @Column(name = "deductible_amount", precision = 18, scale = 2)
    private BigDecimal    deductibleAmount;

    /**
     * 开始日期
     */
    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    /**
     * 结束日期
     */
    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    /**
     * 签发时间
     */
    @Column(name = "issue_time")
    private LocalDateTime issueTime;
}
