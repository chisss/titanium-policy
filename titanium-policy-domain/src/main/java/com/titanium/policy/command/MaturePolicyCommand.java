package com.titanium.policy.command;

import java.math.BigDecimal;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 保单满期给付命令（两全险/生存给付型寿险专属）
 * <p>
 * 被保险人生存至保险期间届满，给付满期生存保险金后保单终止（转 EXPIRED）。区别于普通满期（{@code expire()}
 * 仅止期到达转状态、无给付）：两全险/生存金型产品满期需给付满期金。由定时任务在保单止期到达时对
 * 生存给付型保单触发。
 * </p>
 *
 * @param policyId 保单ID
 * @param maturityBenefit 满期给付金额（满期生存保险金，通常为基本保额或约定比例）
 * @param operatorId 操作人（定时任务系统账号）
 * @param tenantId 租户ID
 */
public record MaturePolicyCommand(
        @TargetAggregateIdentifier String policyId,
        BigDecimal maturityBenefit,
        String operatorId,
        String tenantId
) {
}
