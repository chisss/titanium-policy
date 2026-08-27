package com.titanium.policy.event;

import com.titanium.policy.common.enums.PremiumCollectionStatus;

/**
 * 保费账单关联事件。
 * <p>
 * 该事件是计费/支付单据进入保单事件流的唯一事实来源，读侧据此更新收费明细和统一出单进度。
 * </p>
 *
 * @param policyId        保单ID
 * @param bizNo           统一出单业务流水号
 * @param billId          账单ID
 * @param paymentOrderId  支付单ID（无支付单时为空）
 * @param collectionStatus 保单当前收讫状态
 * @param tenantId        租户ID
 */
public record PremiumBillingAssociatedEvent(String policyId, String bizNo, String billId, String paymentOrderId,
                                            PremiumCollectionStatus collectionStatus, String tenantId) {
}
