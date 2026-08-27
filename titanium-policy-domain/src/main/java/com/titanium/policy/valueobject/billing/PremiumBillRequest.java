package com.titanium.policy.valueobject.billing;

import java.time.LocalDate;
import java.util.List;

import com.titanium.metadata.enums.InsuranceType;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.valueobject.pricing.PremiumCalculationReference;

/**
 * 首期保费账单请求（保单域值对象）
 * <p>
 * 承保出单后为保单开立首期保费账单的领域语义入参，由 {@code BillingServicePort} 的 Adapter 翻译为计费域 DTO。
 * </p>
 *
 * @param policyId      保单ID
 * @param customerId    客户（投保人）ID
 * @param premium       保费金额
 * @param insuranceType 险种（可空）
 * @param dueDate       首期应缴日期（通常为保单起期）
 * @param tenantId      租户ID
 */
public record PremiumBillRequest(String policyId, String customerId, Money premium, InsuranceType insuranceType,
                                 LocalDate dueDate, String tenantId,
                                 List<PremiumCalculationReference> calculationReferences) {

    public PremiumBillRequest {
        calculationReferences = calculationReferences == null ? List.of() : List.copyOf(calculationReferences);
    }

    /** 兼容未提供确认计算引用的普通账单调用方。 */
    public PremiumBillRequest(String policyId, String customerId, Money premium, InsuranceType insuranceType,
                              LocalDate dueDate, String tenantId) {
        this(policyId, customerId, premium, insuranceType, dueDate, tenantId, List.of());
    }

    /** 兼容未提供首期应缴日的既有调用方。 */
    public PremiumBillRequest(String policyId, String customerId, Money premium, InsuranceType insuranceType,
                              String tenantId) {
        this(policyId, customerId, premium, insuranceType, null, tenantId, List.of());
    }
}
