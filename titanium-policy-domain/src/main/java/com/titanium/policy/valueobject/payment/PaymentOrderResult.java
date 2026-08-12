package com.titanium.policy.valueobject.payment;

import com.titanium.metadata.valueobject.Money;

/**
 * 支付单结果值对象（防腐）
 * <p>
 * 支付域创建支付单后的返回。{@link #paymentCredential} 是<b>线上支付的关键返回</b>——
 * 前端凭它唤起支付（支付链接 / 二维码 / 预支付交易串），出单响应需透传给调用方。
 * </p>
 *
 * @param success           是否创建成功
 * @param paymentOrderId    支付单ID
 * @param amount            应付金额
 * @param status            支付状态码
 * @param paymentCredential 支付凭据（线上支付返回给前端唤起支付；线下与免支付为 null）
 * @param failureReason     创建失败原因（成功时为 null）
 */
public record PaymentOrderResult(boolean success, String paymentOrderId, Money amount, String status,
                                 String paymentCredential, String failureReason) {

    /**
     * 构造创建成功结果。
     *
     * @param paymentOrderId    支付单ID
     * @param amount            应付金额
     * @param status            支付状态码
     * @param paymentCredential 支付凭据
     * @return 成功结果
     */
    public static PaymentOrderResult succeeded(String paymentOrderId, Money amount, String status,
                                               String paymentCredential) {
        return new PaymentOrderResult(true, paymentOrderId, amount, status, paymentCredential, null);
    }

    /**
     * 构造创建失败结果。
     * <p>
     * 支付单创建失败<b>不销毁保单</b>——保单停在未生效态，账单待催缴，可重新发起收款。
     * </p>
     *
     * @param reason 失败原因
     * @return 失败结果
     */
    public static PaymentOrderResult failed(String reason) {
        return new PaymentOrderResult(false, null, null, null, null, reason);
    }
}
