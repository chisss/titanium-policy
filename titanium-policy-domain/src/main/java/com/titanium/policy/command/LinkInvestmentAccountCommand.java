package com.titanium.policy.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 挂接投资账户命令（投连/万能保单出单后关联投资账户）
 * <p>
 * 投连险/万能险出单后，投资域为其开立投资账户，本命令将账户引用挂接到保单聚合。
 * 仅投连类形态（{@code PolicyForm.isInvestmentLinked()}）可挂接。
 * </p>
 *
 * @param policyId 保单ID
 * @param investmentAccountId 投资账户ID（investment 域生成）
 * @param operatorId 操作人
 * @param tenantId 租户ID
 */
public record LinkInvestmentAccountCommand(
        @TargetAggregateIdentifier String policyId,
        String investmentAccountId,
        String operatorId,
        String tenantId) {
}
