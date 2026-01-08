package com.titanium.policy.infrastructure.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "t_policy")
@Getter
@Setter
public class PolicyEntity {
    @Id
    @Column(name = "id", nullable = false, length = 32)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId;

    @Column(name = "proposal_id", nullable = false, length = 32)
    private String proposalId;

    @Column(name = "policy_type", nullable = false, length = 32)
    private String policyType;

    @Column(name = "insurance_type", nullable = false, length = 32)
    private String insuranceType;

    @Column(name = "clause_id", nullable = false, length = 32)
    private String clauseId;

    @Column(name = "policy_holder_id", nullable = false, length = 32)
    private String policyHolderId;

    @Column(name = "insured_id", nullable = false, length = 32)
    private String insuredId;

    @Column(name = "sum_insured", nullable = false, precision = 18, scale = 2)
    private BigDecimal sumInsured;

    @Column(name = "add_sum_insured", nullable = false, precision = 18, scale = 2)
    private BigDecimal addSumInsured;

    @Column(name = "premium", nullable = false, precision = 18, scale = 2)
    private BigDecimal premium;

    @Column(name = "policy_status", nullable = false, length = 32)
    private String policyStatus;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Column(name = "create_time", nullable = false, columnDefinition = "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createTime;

    @Column(name = "update_time", nullable = false, columnDefinition = "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private LocalDateTime updateTime;

    @Column(name = "created_by", nullable = false, length = 32)
    private String createdBy;

    @Column(name = "updated_by", nullable = false, length = 32)
    private String updatedBy;

    @Column(name = "is_deleted", nullable = false, columnDefinition = "TINYINT NOT NULL DEFAULT 0")
    private Integer isDeleted;
}
