package com.titanium.policy.valueobject;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.common.enums.PremiumPaymentCycle;
import com.titanium.policy.common.enums.PremiumPaymentMethod;
import com.titanium.policy.common.enums.PremiumPaymentStatus;

/**
 * 保费计划值对象
 * <p>
 * 定义保单的缴费计划，包括保费金额、缴费方式、缴费周期等。
 * 缴费方式/周期/状态枚举已迁至 common/enums（{@link PremiumPaymentMethod}/{@link PremiumPaymentCycle}/
 * {@link PremiumPaymentStatus}），enumCode/code 值保持不变。
 * </p>
 *
 * @param premiumAmount 保费金额
 * @param paymentMethod 缴费方式：趸缴/期缴
 * @param paymentCycle 缴费周期：年/月
 * @param premiumDueDate 保费到期日
 * @param paymentStatus 缴费状态
 */
public record PremiumPlan(Money premiumAmount, PremiumPaymentMethod paymentMethod,
                          PremiumPaymentCycle paymentCycle, LocalDateTime premiumDueDate,
                          PremiumPaymentStatus paymentStatus) {

    /**
     * 计算每期应缴保费
     * <p>
     * 根据缴费方式和缴费周期计算每期应缴保费
     * </p>
     *
     * @return 每期应缴保费
     */
    public Money calculateDuePremium() {
        if (paymentMethod == PremiumPaymentMethod.SINGLE_PAYMENT) {
            // 趸缴情况下，每期应缴保费等于总保费
            return premiumAmount;
        } else {
            // 期缴情况下，根据缴费周期计算每期应缴保费
            switch (paymentCycle) {
                case MONTHLY -> {
                    // 月缴：总保费除以12
                    return Money.of(premiumAmount.value().divide(BigDecimal.valueOf(12)), premiumAmount.currency());
                }
                case QUARTERLY -> {
                    // 季缴：总保费除以4
                    return Money.of(premiumAmount.value().divide(BigDecimal.valueOf(4)), premiumAmount.currency());
                }
                case SEMI_ANNUALLY -> {
                    // 半年缴：总保费除以2
                    return Money.of(premiumAmount.value().divide(BigDecimal.valueOf(2)), premiumAmount.currency());
                }
                case ANNUALLY -> {
                    // 年缴：总保费
                    return premiumAmount;
                }
                default -> throw new IllegalArgumentException("Unknown payment cycle: " + paymentCycle);
            }
        }
    }
}
