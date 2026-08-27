package com.titanium.policy.valueobject.pricing;

import java.math.BigDecimal;

/**
 * 随首期应收传给 Billing 的 Product 确认计算引用。
 */
public record PremiumCalculationReference(
        String calculationId,
        String resultHash,
        String productId,
        String productVersion,
        String pricingPlanVersion,
        BigDecimal totalPremium,
        String currency,
        String lineId) {

    /** 兼容尚未携带险种段标识的历史调用方。 */
    public PremiumCalculationReference(
            String calculationId,
            String resultHash,
            String productId,
            String productVersion,
            String pricingPlanVersion,
            BigDecimal totalPremium,
            String currency) {
        this(calculationId, resultHash, productId, productVersion, pricingPlanVersion, totalPremium, currency, null);
    }
}
