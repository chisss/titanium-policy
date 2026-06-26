package com.titanium.policy.infrastructure.entity;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum.RiskLevel;
import com.titanium.policy.valueobject.SubjectType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 投保意向单标的实体
 * 映射投保意向单标的表，包含标的的所有字段
 */
@Entity
@Table(name = "t_proposal_subject", indexes = {
        @Index(name = "idx_proposal_subject_proposal", columnList = "proposal_id"),
        @Index(name = "idx_proposal_subject_tenant", columnList = "tenant_id")
})
@Getter
@Setter
public class ProposalSubjectEntity {

    @Id
    @Column(name = "subject_id", length = 36, nullable = false)
    private String subjectId;

    @Column(name = "proposal_id", length = 36, nullable = false)
    private String proposalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", length = 20, nullable = false)
    private SubjectType subjectType;

    @Column(name = "simple_info", length = 100, nullable = false)
    private String simpleInfo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estimated_risk_level", length = 20, nullable = false)
    private RiskLevel estimatedRiskLevel;

    @Column(name = "tenant_id", length = 36, nullable = false)
    private String tenantId;
}