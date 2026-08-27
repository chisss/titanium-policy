package com.titanium.policy.valueobject.billing;

/**
 * 计费结果（保单域值对象）
 * <p>
 * 保费账单开立结果，屏蔽计费域返回细节。
 * </p>
 *
 * @param success          是否成功开立
 * @param billId           账单ID（成功时）
 * @param billingAccountId 计费账户ID（成功时，用于同步生成期缴计划）
 */
public record BillingResult(boolean success, String billId, String billingAccountId) {

    /**
     * 兼容不需要计费账户ID的既有调用。
     *
     * @param success 是否成功开立
     * @param billId  账单ID
     */
    public BillingResult(boolean success, String billId) {
        this(success, billId, null);
    }
}
