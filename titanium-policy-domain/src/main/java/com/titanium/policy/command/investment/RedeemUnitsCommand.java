package com.titanium.policy.command.investment;

import java.math.BigDecimal;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 赎回命令（部分领取/退保，按当前单位净值卖出单位数）
 * <p>
 * 赎回金额 = 赎回单位数 × 当前单位净值。赎回后剩余单位数须足以支付后续保障成本。
 * </p>
 *
 * @param accountId 投资账户ID
 * @param redeemUnits 赎回单位数
 * @param reason 赎回原因（PARTIAL_WITHDRAWAL部分领取/SURRENDER退保）
 * @param operatorId 操作人
 */
public record RedeemUnitsCommand(
        @TargetAggregateIdentifier String accountId,
        BigDecimal redeemUnits,
        String reason,
        String operatorId) {
}
