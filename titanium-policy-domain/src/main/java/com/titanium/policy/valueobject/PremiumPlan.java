package com.titanium.policy.valueobject;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.metadata.enums.BaseEnum;
import com.titanium.metadata.valueobject.Money;

import lombok.Getter;

/**
 * 保费计划值对象
 * <p>
 * 定义保单的缴费计划，包括保费金额、缴费方式、缴费周期等
 * </p>
 *
 * @param premiumAmount 保费金额
 * @param paymentMethod 缴费方式：趸缴/期缴
 * @param paymentCycle 缴费周期：年/月
 * @param premiumDueDate 保费到期日
 * @param paymentStatus 缴费状态
 */
public record PremiumPlan(Money premiumAmount, PaymentMethod paymentMethod, PaymentCycle paymentCycle,
                          LocalDateTime premiumDueDate, PaymentStatus paymentStatus) {

    /**
     * 计算每期应缴保费
     * <p>
     * 根据缴费方式和缴费周期计算每期应缴保费
     * </p>
     *
     * @return 每期应缴保费
     */
    public Money calculateDuePremium() {
        if (paymentMethod == PaymentMethod.SINGLE_PAYMENT) {
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

    /**
     * 缴费方式枚举
     */
    @Getter
    public enum PaymentMethod implements BaseEnum {
        /**
         * 趸缴
         */
        SINGLE_PAYMENT(1, "SINGLE_PAYMENT", "趸缴"),
        /**
         * 期缴
         */
        INSTALLMENT_PAYMENT(2, "INSTALLMENT_PAYMENT", "期缴");

        private final Integer enumCode;
        private final String  code;
        private final String  name;

        PaymentMethod(Integer enumCode, String code, String name) {
            this.enumCode = enumCode;
            this.code = code;
            this.name = name;
        }
    }

    /**
     * 缴费周期枚举
     */
    @Getter
    public enum PaymentCycle implements BaseEnum {
        /**
         * 月缴
         */
        MONTHLY(1, "MONTHLY", "月缴"),
        /**
         * 季缴
         */
        QUARTERLY(2, "QUARTERLY", "季缴"),
        /**
         * 半年缴
         */
        SEMI_ANNUALLY(3, "SEMI_ANNUALLY", "半年缴"),
        /**
         * 年缴
         */
        ANNUALLY(4, "ANNUALLY", "年缴");

        private final Integer enumCode;
        private final String  code;
        private final String  name;

        PaymentCycle(Integer enumCode, String code, String name) {
            this.enumCode = enumCode;
            this.code = code;
            this.name = name;
        }
    }

    /**
     * 缴费状态枚举
     */
    @Getter
    public enum PaymentStatus implements BaseEnum {
        /**
         * 未缴费
         */
        UNPAID(1, "UNPAID", "未缴费"),
        /**
         * 已缴费
         */
        PAID(2, "PAID", "已缴费"),
        /**
         * 部分缴费
         */
        PARTIALLY_PAID(3, "PARTIALLY_PAID", "部分缴费"),
        /**
         * 缴费逾期
         */
        OVERDUE(4, "OVERDUE", "缴费逾期"),
        /**
         * 缴费完成
         */
        COMPLETED(5, "COMPLETED", "缴费完成");

        private final Integer enumCode;
        private final String  code;
        private final String  name;

        PaymentStatus(Integer enumCode, String code, String name) {
            this.enumCode = enumCode;
            this.code = code;
            this.name = name;
        }
    }
}
