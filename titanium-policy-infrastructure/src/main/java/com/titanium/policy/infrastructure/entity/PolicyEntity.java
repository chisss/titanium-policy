package com.titanium.policy.infrastructure.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 正式保单数据库实体
 * <p>
 * 对应t_policy表，存储正式保单的基本信息
 * </p>
 */
@Entity
@Table(name = "t_policy")
@Getter
@Setter
public class PolicyEntity {
    /**
     * 保单ID
     */
    @Id
    @Column(name = "policy_id", nullable = false, length = 32)
    private String        policyId;

    /**
     * 租户ID
     */
    @Column(name = "tenant_id", nullable = false, length = 32)
    private String        tenantId;

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
    @Column(name = "policy_form", nullable = false, length = 32)
    private String        policyForm;

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
    @Column(name = "policy_status", nullable = false, length = 32)
    private String        policyStatus;

    /**
     * 保单类型
     */
    @Column(name = "policy_type", nullable = false, length = 32)
    private String        policyType;

    /**
     * 保险类型
     */
    @Column(name = "insurance_type", nullable = false, length = 32)
    private String        insuranceType;

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
     * 创建时间
     */
    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    /**
     * 签发时间
     */
    @Column(name = "issue_time")
    private LocalDateTime issueTime;

    /**
     * 更新时间
     */
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    /**
     * 创建人
     */
    @Column(name = "created_by", nullable = false, length = 32)
    private String        createdBy;

    /**
     * 更新人
     */
    @Column(name = "updated_by", nullable = false, length = 32)
    private String        updatedBy;

    /**
     * 是否删除
     */
    @Column(name = "is_deleted", nullable = false, columnDefinition = "TINYINT NOT NULL DEFAULT 0")
    private Integer       isDeleted;
}
