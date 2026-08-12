package com.titanium.policy.event;

import java.time.LocalDateTime;

import com.titanium.metadata.enums.billing.BillingEnum.PaymentMethod;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.common.enums.PremiumCollectionStatus;

/**
 * 保费收讫事件
 * <p>
 * 记录一笔保费实收，并携带累计后的收讫状态（已收讫 / 部分收讫）。读侧据此更新
 * {@code t_policy_collection} 与 {@code t_policy_view} 的收费列；收讫状态转为已收讫时，
 * 收费 Saga 据此驱动保单生效。
 * </p>
 *
 * @param policyId           保单ID
 * @param paymentId          支付流水ID
 * @param paymentNo          支付流水号
 * @param collectedAmount    本次实收金额
 * @param accumulatedAmount  累计实收金额
 * @param collectionStatus   累计后的收讫状态
 * @param paymentMethod      支付方式
 * @param collectedTime      实收时间
 * @param operatorId         操作人ID
 * @param tenantId           租户ID
 */
public record PremiumCollectedEvent(String policyId, String paymentId, String paymentNo, Money collectedAmount,
                                    Money accumulatedAmount, PremiumCollectionStatus collectionStatus,
                                    PaymentMethod paymentMethod, LocalDateTime collectedTime, String operatorId,
                                    String tenantId) {
}
