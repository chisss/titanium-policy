package com.titanium.policy.infrastructure.entity;

import java.math.BigDecimal;

import com.titanium.metadata.enums.CurrencyEnum;

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
 * 投保单险种实体
 * 映射投保单险种表，包含险种的所有字段
 */
@Entity
@Table(name = "t_insurance_product", indexes = {
        @Index(name = "idx_product_insurance", columnList = "insurance_id"),
        @Index(name = "idx_product_tenant", columnList = "tenant_id")
})
@Getter
@Setter
public class InsuranceProductEntity {

    @Id
    @Column(name = "product_id", length = 36, nullable = false)
    private String productId;

    @Column(name = "insurance_id", length = 36, nullable = false)
    private String insuranceId;

    @Column(name = "product_code", length = 20, nullable = false)
    private String productCode;

    @Column(name = "product_name", length = 50, nullable = false)
    private String productName;

    @Column(name = "sum_insured", precision = 18, scale = 2, nullable = false)
    private BigDecimal sumInsured;

    @Enumerated(EnumType.STRING)
    @Column(name = "sum_insured_currency", length = 8, nullable = false)
    private CurrencyEnum sumInsuredCurrency;

    @Column(name = "premium_factor", nullable = false)
    private double premiumFactor;

    @Column(name = "is_main_line", nullable = false)
    private boolean isMainLine;

    @Column(name = "tenant_id", length = 36, nullable = false)
    private String tenantId;
}