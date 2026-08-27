package com.titanium.policy.valueobject.pricing;

import java.math.BigDecimal;

/**
 * 出单确认计算的核保调整输入。
 */
public record PremiumAdjustmentInput(
        String adjustmentCode,
        String type,
        BigDecimal value,
        String reason,
        String ruleVersion) {
}
