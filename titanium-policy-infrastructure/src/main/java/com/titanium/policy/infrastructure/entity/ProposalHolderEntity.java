package com.titanium.policy.infrastructure.entity;

import com.titanium.metadata.enums.customer.CustomerEnum.IdCardType;

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
 * 投保意向单投保人实体
 * 映射投保意向单投保人表，包含投保人的所有字段
 */
@Entity
@Table(name = "t_proposal_holder", indexes = {
        @Index(name = "idx_proposal_holder_proposal", columnList = "proposal_id"),
        @Index(name = "idx_proposal_holder_tenant", columnList = "tenant_id")
})
@Getter
@Setter
public class ProposalHolderEntity {

    @Id
    @Column(name = "holder_id", length = 36, nullable = false)
    private String holderId;

    @Column(name = "proposal_id", length = 36, nullable = false)
    private String proposalId;

    @Column(name = "name", length = 50, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "cert_type", length = 30, nullable = false)
    private IdCardType certType;

    @Column(name = "cert_no", length = 20, nullable = false)
    private String certNo;

    @Column(name = "phone", length = 15)
    private String phone;

    @Column(name = "is_insured", nullable = false)
    private boolean isInsured;

    @Column(name = "tenant_id", length = 36, nullable = false)
    private String tenantId;
}
