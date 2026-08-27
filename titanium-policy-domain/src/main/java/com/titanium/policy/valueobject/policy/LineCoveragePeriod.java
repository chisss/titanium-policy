package com.titanium.policy.valueobject.policy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.titanium.metadata.enums.product.ProductEnum.CoveragePeriodType;
import com.titanium.metadata.enums.product.ProductEnum.PeriodUnit;

/**
 * 险种段保障期间值对象
 * <p>
 * 险种段（{@code PolicyProduct}）的保障期间<b>可独立于保单主期间</b>——这是一单多险的必要能力：
 * 「终身寿主险（终身） + 附加重疾（至 70 岁） + 附加医疗（1 年期年年续）」三段期间各不相同，
 * 无法用保单级的单一起止期表达。
 * </p>
 *
 * @param periodType  保障期间类型（固定期限 / 终身 / 自定义）
 * @param periodStart 保障起期
 * @param periodEnd   保障止期（终身型为 null）
 * @param periodValue 期限数值（如 20 表示 20 年 / 至 70 岁的年数，终身型为 null）
 * @param periodUnit  期限单位（年 / 月 / 日）
 */
public record LineCoveragePeriod(CoveragePeriodType periodType, LocalDateTime periodStart, LocalDateTime periodEnd,
                                 Integer periodValue, PeriodUnit periodUnit) {

    /**
     * 构建固定期限保障期间。
     *
     * @param start       保障起期
     * @param end         保障止期
     * @param periodValue 期限数值
     * @param periodUnit  期限单位
     * @return 固定期限保障期间
     */
    public static LineCoveragePeriod fixedTerm(LocalDateTime start, LocalDateTime end, Integer periodValue,
                                               PeriodUnit periodUnit) {
        return new LineCoveragePeriod(CoveragePeriodType.FIXED_TERM, start, end, periodValue, periodUnit);
    }

    /**
     * 构建终身保障期间（无止期）。
     *
     * @param start 保障起期
     * @return 终身保障期间
     */
    public static LineCoveragePeriod wholeLife(LocalDateTime start) {
        return new LineCoveragePeriod(CoveragePeriodType.WHOLE_LIFE, start, null, null, null);
    }

    /**
     * 是否为终身保障（无止期）。
     *
     * @return 终身型返回 {@code true}
     */
    @JsonIgnore
    public boolean isWholeLife() {
        return periodType == CoveragePeriodType.WHOLE_LIFE || periodEnd == null;
    }

    /**
     * 指定时点是否处于保障期内。
     * <p>
     * 终身型仅校验起期已到；固定期限型校验落在起止期之间（含起期，不含止期次日）。
     * </p>
     *
     * @param at 判定时点
     * @return 在保障期内返回 {@code true}
     */
    public boolean covers(LocalDateTime at) {
        if (at == null || periodStart == null || at.isBefore(periodStart)) {
            return false;
        }
        return isWholeLife() || !at.isAfter(periodEnd);
    }

    /**
     * 保障年限（供保费计算使用）。
     * <p>
     * 终身型返回 0（由精算侧按终身口径处理）；固定期限型按起止期计算整年数。
     * </p>
     *
     * @return 保障年限
     */
    public int coverageYears() {
        if (isWholeLife() || periodStart == null) {
            return 0;
        }
        if (periodValue != null && periodValue > 0 && periodUnit != null) {
            return switch (periodUnit) {
                case YEAR -> periodValue;
                // Product pricing accepts a year dimension; a sub-year cover is one pricing year.
                case MONTH, DAY -> 1;
            };
        }
        if (periodEnd == null || periodEnd.isBefore(periodStart)) {
            return 0;
        }
        // 保险止期通常按“周年日前一日”表示，先转为排他上界再计算自然年。
        LocalDate start = periodStart.toLocalDate();
        LocalDate exclusiveEnd = periodEnd.toLocalDate().plusDays(1);
        long fullYears = ChronoUnit.YEARS.between(start, exclusiveEnd);
        return (int) Math.max(1, fullYears);
    }
}
