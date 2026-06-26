package com.titanium.policy.event.investment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 赎回已完成事件（卖出投资单位）
 */
public record UnitsRedeemedEvent(
        String accountId,
        BigDecimal redeemUnits,
        BigDecimal unitPrice,
        BigDecimal redeemAmount,
        BigDecimal remainingUnits,
        String reason,
        LocalDateTime redeemedAt,
        String operatorId) {
}
