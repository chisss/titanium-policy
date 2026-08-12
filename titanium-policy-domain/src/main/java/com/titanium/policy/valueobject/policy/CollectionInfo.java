package com.titanium.policy.valueobject.policy;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.titanium.metadata.enums.billing.PremiumCollectionMode;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.common.enums.PremiumCollectionStatus;

/**
 * 收费信息值对象
 * <p>
 * 承载保单保费的收取事实：收费方式、关联账单与支付单、应收与实收金额、收讫状态。
 * 此前 policy 域<b>无收费概念</b>——出单 Saga 只向 billing 开账单却不做收费，而
 * {@code Policy.activate()} 要求首期已缴，因 {@code premiumPlan} 恒 null 使该校验被短路，
 * 形成「未收钱也能生效」的漏洞。本值对象是修复该漏洞的载体。
 * </p>
 * <p>
 * 🔴 <b>生效前提由收费方式决定</b>：先享后付（{@code PAY_AFTER_USE}）保障先行、账单后付，
 * 不以收讫为生效前提；其余方式须收讫方可生效。判定内聚于 {@link #allowsActivation()}。
 * </p>
 *
 * @param collectionMode   收费方式（线下 / 线上 / 免支付 / 先享后付 / 代扣）
 * @param billId           账单ID（billing 域）
 * @param paymentOrderId   支付单ID（payment 域；线下与免支付无支付单）
 * @param payableAmount    应收金额
 * @param collectedAmount  已收金额（零元账单为 0 且状态为已收讫）
 * @param collectionStatus 收讫状态（保单维度聚合状态）
 * @param collectedTime    收讫时间（未收讫为 null）
 */
public record CollectionInfo(PremiumCollectionMode collectionMode, String billId, String paymentOrderId,
                             Money payableAmount, Money collectedAmount, PremiumCollectionStatus collectionStatus,
                             LocalDateTime collectedTime) {

    /**
     * 构建出单时点的初始收费信息。
     * <p>
     * 免支付（零元保）直接视为已收讫；先享后付标记为后付（保单可直接生效）；
     * 其余方式为未收讫，待收费回调驱动。
     * </p>
     *
     * @param mode          收费方式
     * @param payableAmount 应收金额
     * @param at            出单时点
     * @return 初始收费信息
     */
    public static CollectionInfo initial(PremiumCollectionMode mode, Money payableAmount, LocalDateTime at) {
        if (mode == null) {
            return new CollectionInfo(null, null, null, payableAmount, zeroLike(payableAmount),
                    PremiumCollectionStatus.UNCOLLECTED, null);
        }
        if (mode.isSettledOnIssue()) {
            return new CollectionInfo(mode, null, null, payableAmount, payableAmount,
                    PremiumCollectionStatus.COLLECTED, at);
        }
        if (mode.allowsActivationWithoutPayment()) {
            return new CollectionInfo(mode, null, null, payableAmount, zeroLike(payableAmount),
                    PremiumCollectionStatus.DEFERRED, null);
        }
        return new CollectionInfo(mode, null, null, payableAmount, zeroLike(payableAmount),
                PremiumCollectionStatus.UNCOLLECTED, null);
    }

    /**
     * 关联账单与支付单（收费编排开单后回填）。
     *
     * @param billId         账单ID
     * @param paymentOrderId 支付单ID（无支付单传 null）
     * @return 关联后的新实例
     */
    public CollectionInfo withBilling(String billId, String paymentOrderId) {
        return new CollectionInfo(collectionMode, billId, paymentOrderId, payableAmount, collectedAmount,
                collectionStatus, collectedTime);
    }

    /**
     * 记录实收（收费回调驱动）。
     * <p>
     * 累计实收金额，达到应收即转「已收讫」，未达则为「部分收讫」。
     * </p>
     *
     * @param amount      本次实收金额
     * @param collectedAt 实收时间
     * @return 记录后的新实例
     */
    public CollectionInfo collect(Money amount, LocalDateTime collectedAt) {
        if (amount == null) {
            return this;
        }
        Money accumulated = collectedAmount != null ? collectedAmount.add(amount) : amount;
        boolean settled = payableAmount == null || accumulated.value().compareTo(payableAmount.value()) >= 0;
        return new CollectionInfo(collectionMode, billId, paymentOrderId, payableAmount, accumulated,
                settled ? PremiumCollectionStatus.COLLECTED : PremiumCollectionStatus.PARTIALLY_COLLECTED,
                collectedAt);
    }

    /**
     * 标记为逾期（宽限期满未收讫，触发保单失效流程）。
     *
     * @return 逾期状态的新实例
     */
    public CollectionInfo markOverdue() {
        return new CollectionInfo(collectionMode, billId, paymentOrderId, payableAmount, collectedAmount,
                PremiumCollectionStatus.OVERDUE, collectedTime);
    }

    /**
     * 保费是否已全额收讫。
     *
     * @return 已收讫返回 {@code true}
     */
    @JsonIgnore
    public boolean isFullyCollected() {
        return collectionStatus == PremiumCollectionStatus.COLLECTED;
    }

    /**
     * 收费条件是否允许保单生效。
     * <p>
     * 已收讫，或收费方式本身不以收讫为生效前提（先享后付），均允许生效。
     * </p>
     *
     * @return 允许生效返回 {@code true}
     */
    public boolean allowsActivation() {
        return collectionStatus != null && collectionStatus.allowsActivation();
    }

    /**
     * 构造与给定金额同币种的零金额（金额为空时返回 null）。
     */
    private static Money zeroLike(Money reference) {
        return reference != null ? Money.zero(reference.currency()) : null;
    }
}
