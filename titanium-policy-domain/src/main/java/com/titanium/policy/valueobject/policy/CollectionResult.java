package com.titanium.policy.valueobject.policy;

import com.titanium.policy.common.enums.PremiumCollectionStatus;

/**
 * 收费编排结果值对象
 * <p>
 * 收费编排（{@code PremiumCollectionOrchestrator}）的返回，表达「钱收到什么程度」以及
 * 「调用方还要不要做动作」：{@link #paymentCredential} 非空时前端需唤起支付。
 * </p>
 *
 * @param status            收讫状态（已收讫 / 后付挂账 / 未收讫待支付）
 * @param billId            账单ID（billing 域；开单失败时为 null，待补偿）
 * @param billingAccountId  计费账户ID（同步开账返回，供期缴计划使用）
 * @param paymentOrderId    支付单ID（线上支付与代扣有值）
 * @param paymentCredential 支付凭据（线上支付返回给前端唤起支付）
 * @param skipReason        跳过收费的原因（未指定收费方式等异常场景）
 */
public record CollectionResult(PremiumCollectionStatus status, String billId, String billingAccountId,
                               String paymentOrderId,
                               String paymentCredential, String skipReason) {

    /**
     * 构造已收讫结果（免支付零元单）。
     *
     * @param billId 账单ID
     * @return 已收讫结果
     */
    public static CollectionResult settled(String billId) {
        return settled(billId, null);
    }

    /**
     * 构造带计费账户的已收讫结果。
     */
    public static CollectionResult settled(String billId, String billingAccountId) {
        return new CollectionResult(PremiumCollectionStatus.COLLECTED, billId, billingAccountId, null, null, null);
    }

    /**
     * 构造后付挂账结果（先享后付：保单可直接生效）。
     *
     * @param billId 账单ID
     * @return 后付结果
     */
    public static CollectionResult deferred(String billId) {
        return deferred(billId, null);
    }

    /**
     * 构造带计费账户的后付结果。
     */
    public static CollectionResult deferred(String billId, String billingAccountId) {
        return new CollectionResult(PremiumCollectionStatus.DEFERRED, billId, billingAccountId, null, null, null);
    }

    /**
     * 构造待收讫结果（线上支付/代扣/线下，等回调驱动）。
     *
     * @param billId            账单ID
     * @param paymentOrderId    支付单ID（线下无支付单传 null）
     * @param paymentCredential 支付凭据（线下传 null）
     * @return 待收讫结果
     */
    public static CollectionResult pending(String billId, String paymentOrderId, String paymentCredential) {
        return pending(billId, null, paymentOrderId, paymentCredential);
    }

    /**
     * 构造带计费账户的待收讫结果。
     */
    public static CollectionResult pending(String billId, String billingAccountId, String paymentOrderId,
                                           String paymentCredential) {
        return new CollectionResult(PremiumCollectionStatus.UNCOLLECTED, billId, billingAccountId, paymentOrderId,
                paymentCredential, null);
    }

    /**
     * 构造跳过收费结果（未指定收费方式等异常场景，需人工补正）。
     *
     * @param reason 跳过原因
     * @return 跳过结果
     */
    public static CollectionResult skipped(String reason) {
        return new CollectionResult(PremiumCollectionStatus.UNCOLLECTED, null, null, null, null, reason);
    }

    /**
     * 保单是否已满足生效的保费条件（已收讫或后付挂账）。
     *
     * @return 满足返回 {@code true}
     */
    public boolean allowsActivation() {
        return status != null && status.allowsActivation();
    }

    /**
     * 是否需要调用方引导付款（返回了支付凭据）。
     *
     * @return 需引导付款返回 {@code true}
     */
    public boolean requiresPaymentAction() {
        return paymentCredential != null && !paymentCredential.isBlank();
    }
}
