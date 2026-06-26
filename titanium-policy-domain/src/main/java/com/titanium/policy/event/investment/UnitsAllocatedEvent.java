package com.titanium.policy.event.investment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 申购已完成事件（买入投资单位）
 */
public record UnitsAllocatedEvent(
        String accountId,
        BigDecimal allocationAmount,
        BigDecimal unitPrice,
        BigDecimal allocatedUnits,
        BigDecimal totalUnits,
        String source,
        LocalDateTime allocatedAt,
        String operatorId) {
}
