package com.titanium.policy.valueobject.billing;

/**
 * 计费结果（保单域值对象）
 * <p>
 * 保费账单开立结果，屏蔽计费域返回细节。
 * </p>
 *
 * @param success 是否成功开立
 * @param billId  账单ID（成功时）
 */
public record BillingResult(boolean success, String billId) {
}
