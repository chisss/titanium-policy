package com.titanium.policy.command.investment;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 调整单位净值命令（每日估值，由定时任务触发）
 * <p>
 * 投连险投资账户单位净值随市场波动每日更新，账户价值 = 持有单位数 × 新单位净值。
 * </p>
 *
 * @param accountId 投资账户ID
 * @param newUnitPrice 新单位净值
 * @param valuationDate 估值日期
 */
public record AdjustUnitPriceCommand(
        @TargetAggregateIdentifier String accountId,
        BigDecimal newUnitPrice,
        LocalDate valuationDate) {
}
