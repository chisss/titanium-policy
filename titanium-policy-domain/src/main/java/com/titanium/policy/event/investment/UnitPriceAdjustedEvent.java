package com.titanium.policy.event.investment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 单位净值已调整事件（每日估值）
 */
public record UnitPriceAdjustedEvent(
        String accountId,
        BigDecimal oldUnitPrice,
        BigDecimal newUnitPrice,
        BigDecimal totalUnits,
        BigDecimal accountValue,
        LocalDate valuationDate,
        LocalDateTime adjustedAt) {
}
