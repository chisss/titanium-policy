package com.titanium.policy.valueobject.payment;

import com.titanium.metadata.enums.billing.PremiumCollectionMode;
import com.titanium.metadata.valueobject.Money;

/**
 * 保费支付请求值对象（防腐）
 * <p>
 * 出单收费编排经 {@code PaymentServicePort} 向支付域发起收款时的入参。收费方式决定支付通道：
 * 线上支付走网关（返回支付凭据给前端）、代扣走签约扣款；线下与免支付不建支付单。
 * </p>
 *
 * @param policyId       保单ID（支付单的业务单据ID）
 * @param billId         账单ID（billing 域，对账依据）
 * @param customerId     付款客户ID（投保人）
 * @param amount         应付金额
 * @param collectionMode 收费方式（决定支付通道）
 * @param description    支付描述（展示给付款人，如「XX保险首期保费」）
 * @param tenantId       租户ID
 */
public record PremiumPaymentRequest(String policyId, String billId, String customerId, Money amount,
                                    PremiumCollectionMode collectionMode, String description, String tenantId) {
}
