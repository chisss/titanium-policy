package com.titanium.policy.valueobject.payment;

import java.time.LocalDateTime;

import com.titanium.metadata.valueobject.Money;

/**
 * 保费实收回写通知值对象（防腐）
 * <p>
 * 支付域 {@code payment-order-paid} 主题的支付成功事实在保单域的领域表达：出单收费编排
 * 建单后返回「待支付回调」，回调由本值对象承接，经 {@code RecordPremiumCollectionCommand}
 * 把实收写回保单事件流，收讫后驱动保单生效（{@code ActivatePolicyCommand}）。
 * </p>
 * <p>
 * 补齐此前的断链：{@code RecordPremiumCollectionCommand} 的处理器虽已存在（含按 paymentId 去重的
 * 幂等校验），但全库零调用方 —— 收费方式为线上/代扣/线下的保单永远停在未生效态，
 * {@code Policy.canActivate()} 的保费条件无从被真实支付结果满足。
 * </p>
 *
 * @param policyId        保单ID（支付单的业务单号 businessId）
 * @param paymentId       支付流水ID（payment 域，收讫幂等键）
 * @param paymentNo       支付流水号（对账凭证号）
 * @param collectedAmount 本次实收金额
 * @param collectedAt     实收时间（支付成功时间）
 * @param tenantId        租户ID
 */
public record PremiumCollectionNotice(String policyId, String paymentId, String paymentNo, Money collectedAmount,
                                      LocalDateTime collectedAt, String tenantId) {
}
