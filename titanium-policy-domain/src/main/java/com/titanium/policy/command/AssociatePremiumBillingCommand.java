package com.titanium.policy.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 关联保费账单命令。
 * <p>
 * 收费编排完成开单后，将计费域账单和支付域支付单写回保单事件流。该命令只记录单据关联，
 * 不代表已经收到保费；实收仍由 {@link RecordPremiumCollectionCommand} 独立记录。
 * </p>
 *
 * @param policyId      保单ID
 * @param billId        账单ID
 * @param paymentOrderId 支付单ID（线下、免支付和后付场景为空）
 * @param tenantId      租户ID
 */
public record AssociatePremiumBillingCommand(@TargetAggregateIdentifier String policyId, String billId,
                                             String paymentOrderId, String tenantId) {
}
