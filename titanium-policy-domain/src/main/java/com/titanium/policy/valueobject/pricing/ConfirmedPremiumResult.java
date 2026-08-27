package com.titanium.policy.valueobject.pricing;

import java.math.BigDecimal;

/**
 * Product 已持久化的确认保费事实。
 */
public record ConfirmedPremiumResult(
        String calculationId,
        String status,
        String purpose,
        String productId,
        String productVersion,
        String currency,
        BigDecimal standardPremium,
        BigDecimal totalPremium,
        String pricingPlanVersion,
        String resultHash) {
}
