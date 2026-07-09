package com.titanium.policy.valueobject.billing;

import com.titanium.metadata.enums.InsuranceType;
import com.titanium.metadata.valueobject.Money;

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
 * @param tenantId      租户ID
 */
public record PremiumBillRequest(String policyId, String customerId, Money premium, InsuranceType insuranceType,
                                 String tenantId) {
}
