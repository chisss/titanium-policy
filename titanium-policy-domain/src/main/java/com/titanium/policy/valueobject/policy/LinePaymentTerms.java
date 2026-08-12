package com.titanium.policy.valueobject.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.titanium.metadata.enums.product.ProductEnum.PaymentFrequency;
import com.titanium.metadata.valueobject.Money;

/**
 * 险种段缴费条件值对象
 * <p>
 * 险种段的缴费方式与缴费期<b>可独立于其他段</b>——「终身寿主险 20 年缴 + 附加医疗年缴年年续」
 * 是寿险常态，无法用保单级单一缴费条件表达。
 * </p>
 * <p>
 * 🔴 <b>缴费期 ≠ 保障期</b>：20 年缴的终身寿险，缴费 20 年而保障终身。二者分别由本值对象与
 * {@link LineCoveragePeriod} 承载，不可混用。
 * </p>
 * <p>
 * 期数与每期金额的计算内聚于此（原散落在 {@code IssuanceSaga} 的私有方法 {@code calculateTotalPeriods}
 * / {@code calculateInstallmentAmount} 属业务规则，不应留在编排器）。
 * </p>
 *
 * @param paymentFrequency    缴费频率（趸缴 / 年缴 / 半年缴 / 季缴 / 月缴）
 * @param premiumPaymentYears 缴费年数（趸缴为 1；终身缴可为保障年数）
 */
public record LinePaymentTerms(PaymentFrequency paymentFrequency, int premiumPaymentYears) {

    /** 每年月数 */
    private static final int MONTHS_PER_YEAR = 12;
    /** 每年季数 */
    private static final int QUARTERS_PER_YEAR = 4;
    /** 每年半年数 */
    private static final int HALF_YEARS_PER_YEAR = 2;
    /** 金额计算精度 */
    private static final int AMOUNT_SCALE = 2;

    /**
     * 构建趸缴条件（一次缴清）。
     *
     * @return 趸缴条件
     */
    public static LinePaymentTerms lumpSum() {
        return new LinePaymentTerms(PaymentFrequency.LUMP_SUM, 1);
    }

    /**
     * 构建年缴条件。
     *
     * @param years 缴费年数
     * @return 年缴条件
     */
    public static LinePaymentTerms annual(int years) {
        return new LinePaymentTerms(PaymentFrequency.ANNUAL, years);
    }

    /**
     * 是否趸缴（一次缴清）。
     *
     * @return 趸缴返回 {@code true}
     */
    @JsonIgnore
    public boolean isLumpSum() {
        return paymentFrequency == PaymentFrequency.LUMP_SUM;
    }

    /**
     * 总缴费期数。
     * <p>
     * 趸缴恒为 1 期；期缴按缴费年数 × 每年期数推算。缴费年数非正时按 1 期兜底，
     * 避免除零。
     * </p>
     *
     * @return 总期数
     */
    public int totalPeriods() {
        if (isLumpSum() || premiumPaymentYears <= 0) {
            return 1;
        }
        return switch (paymentFrequency) {
            case MONTHLY -> premiumPaymentYears * MONTHS_PER_YEAR;
            case QUARTERLY -> premiumPaymentYears * QUARTERS_PER_YEAR;
            case SEMI_ANNUAL -> premiumPaymentYears * HALF_YEARS_PER_YEAR;
            case ANNUAL -> premiumPaymentYears;
            case LUMP_SUM -> 1;
        };
    }

    /**
     * 每期应缴金额（总保费 ÷ 总期数，四舍五入到分）。
     *
     * @param totalPremium 该险种段总保费
     * @return 每期应缴金额；总保费为空时返回 null
     */
    public Money installmentAmount(Money totalPremium) {
        if (totalPremium == null) {
            return null;
        }
        int periods = totalPeriods();
        if (periods <= 1) {
            return totalPremium;
        }
        BigDecimal perPeriod = totalPremium.value()
                .divide(BigDecimal.valueOf(periods), AMOUNT_SCALE, RoundingMode.HALF_UP);
        return Money.of(perPeriod, totalPremium.currency());
    }
}
