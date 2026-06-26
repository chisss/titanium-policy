package com.titanium.policy.command.investment;

import java.math.BigDecimal;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.policy.valueobject.InvestmentAccountType;

/**
 * 创建投资账户命令（投连险/万能险出单时触发）
 *
 * @param accountId 投资账户ID
 * @param policyId 关联保单ID
 * @param accountType 账户类型（UNIT_LINKED投连/UNIVERSAL万能）
 * @param initialUnitPrice 初始单位净值
 * @param currency 币种
 * @param managementFeeRate 管理费率（年化）
 * @param tenantId 租户ID
 */
public record CreateInvestmentAccountCommand(
        @TargetAggregateIdentifier String accountId,
        String policyId,
        InvestmentAccountType accountType,
        BigDecimal initialUnitPrice,
        String currency,
        BigDecimal managementFeeRate,
        String tenantId) {
}
