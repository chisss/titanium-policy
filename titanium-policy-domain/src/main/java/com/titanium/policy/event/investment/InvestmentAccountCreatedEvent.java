package com.titanium.policy.event.investment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.policy.valueobject.InvestmentAccountType;

/**
 * 投资账户已创建事件
 */
public record InvestmentAccountCreatedEvent(
        String accountId,
        String policyId,
        InvestmentAccountType accountType,
        BigDecimal initialUnitPrice,
        String currency,
        BigDecimal managementFeeRate,
        String status,
        LocalDateTime createdAt,
        String tenantId) {
}
