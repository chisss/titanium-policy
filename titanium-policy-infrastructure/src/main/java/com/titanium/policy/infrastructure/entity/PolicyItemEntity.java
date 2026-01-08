package com.titanium.policy.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "t_policy_item")
@Getter
@Setter
public class PolicyItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "item_id")
    private String  itemId;

    @Column(name = "policy_id", nullable = false)
    private String  policyId;

    @Column(name = "coverage_id", nullable = false)
    private String  coverageId;

    @Column(name = "coverage_code", nullable = false)
    private String  coverageCode;

    @Column(name = "coverage_name", nullable = false)
    private String  coverageName;

    @Column(name = "coverage_description")
    private String  coverageDescription;

    @Column(name = "sum_insured", nullable = false)
    private Double  sumInsured;

    @Column(name = "sum_insured_currency", nullable = false)
    private String  sumInsuredCurrency;

    @Column(name = "premium", nullable = false)
    private Double  premium;

    @Column(name = "premium_currency", nullable = false)
    private String  premiumCurrency;

    @Column(name = "deductible", nullable = false)
    private Integer deductible;

    @Column(name = "coinsurance", nullable = false)
    private Integer coinsurance;

    @Column(name = "coverage_active", nullable = false)
    private Boolean coverageActive;

    @Column(name = "tenant_id", nullable = false)
    private String  tenantId;
}
