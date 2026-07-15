package com.titanium.policy.event;

import java.time.LocalDateTime;

import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.common.enums.AnnuityPayoutStatus;

/**
 * 年金给付一期已完成事件
 * <p>
 * 年金给付期内按频率给付一期生存年金，据此在读侧推进年金给付计划的已给付期数、下一给付日与状态。
 * 年金给付<b>不终止保单</b>，保单状态不受本事件影响。
 * </p>
 *
 * @param policyId 保单ID
 * @param installmentNo 本次给付期序（第几期）
 * @param amount 本期给付金额
 * @param paidInstallments 累计已给付期数
 * @param nextPayoutDate 下一给付日
 * @param status 给付后计划状态（PAYING/COMPLETED）
 * @param operatorId 操作人ID
 * @param occurredAt 事件发生时间
 * @param tenantId 租户ID
 */
public record AnnuityBenefitPaidEvent(String policyId, int installmentNo, Money amount, int paidInstallments,
                                      LocalDateTime nextPayoutDate, AnnuityPayoutStatus status, String operatorId,
                                      LocalDateTime occurredAt, String tenantId) {
}
