package com.titanium.policy.event;

import java.time.LocalDateTime;

import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.common.enums.AnnuityPayoutFrequency;

/**
 * 年金给付期已启动事件
 * <p>
 * 年金保险保单进入给付期，据此在读侧维护年金给付计划（{@code t_annuity_payout_plan}）。
 * </p>
 *
 * @param policyId 保单ID
 * @param startDate 给付起始日
 * @param frequency 给付频率
 * @param amountPerInstallment 每期给付金额
 * @param totalInstallments 总给付期数（null 表示终身年金）
 * @param nextPayoutDate 下一给付日
 * @param operatorId 操作人ID
 * @param occurredAt 事件发生时间
 * @param tenantId 租户ID
 */
public record AnnuityPayoutStartedEvent(String policyId, LocalDateTime startDate, AnnuityPayoutFrequency frequency,
                                        Money amountPerInstallment, Integer totalInstallments,
                                        LocalDateTime nextPayoutDate, String operatorId, LocalDateTime occurredAt,
                                        String tenantId) {
}
