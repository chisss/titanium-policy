package com.titanium.policy.command.investment;

import java.math.BigDecimal;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 申购命令（保费投入投资账户，按当前单位净值买入单位数）
 * <p>
 * 投连险保费扣除风险保费与费用后，剩余部分申购投资单位：
 * 买入单位数 = 申购金额 / 当前单位净值。
 * </p>
 *
 * @param accountId 投资账户ID
 * @param allocationAmount 申购金额
 * @param source 资金来源（PREMIUM保费/ADDITIONAL追加）
 * @param operatorId 操作人
 */
public record AllocateUnitsCommand(
        @TargetAggregateIdentifier String accountId,
        BigDecimal allocationAmount,
        String source,
        String operatorId) {
}
