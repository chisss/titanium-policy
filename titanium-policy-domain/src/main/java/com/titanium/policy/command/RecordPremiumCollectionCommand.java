package com.titanium.policy.command;

import java.time.LocalDateTime;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.metadata.enums.billing.BillingEnum.PaymentMethod;
import com.titanium.metadata.valueobject.Money;

/**
 * 记录保费收讫命令（收费回调驱动）
 * <p>
 * 由 billing / payment 域的收费回调经防腐监听器转译后发出，将实收事实回写保单。收讫后
 * 保单方满足生效的保费条件（见 {@code Policy.canActivate()}）。
 * </p>
 * <p>
 * 补齐此前的断链：原 {@code Policy.recordPayment(PaymentRecord)} 是普通方法而非
 * {@code @CommandHandler}，全库零调用方——收费事实无从进入保单。
 * </p>
 *
 * @param policyId       保单ID
 * @param paymentId      支付流水ID（payment 域）
 * @param paymentNo      支付流水号
 * @param collectedAmount 本次实收金额
 * @param paymentMethod  支付方式
 * @param collectedTime  实收时间
 * @param operatorId     操作人ID（系统回调为系统标识）
 * @param tenantId       租户ID
 */
public record RecordPremiumCollectionCommand(@TargetAggregateIdentifier String policyId, String paymentId,
                                             String paymentNo, Money collectedAmount, PaymentMethod paymentMethod,
                                             LocalDateTime collectedTime, String operatorId, String tenantId) {
}
