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
 * <p>
 * 🔴 <b>等待期/犹豫期自 G11/g11-04（D2 裁决）下沉至此</b>：二者是<b>段级</b>要素——同一保单下
 * 「交强险（法定、无等待期）+ 商业车险」各段取值本就不同，保单级单一取值表达不了，故随
 * {@code PolicyProduct} 快照在<b>段上</b>冻结，保单级 {@code PolicyPeriod} 不再持有该两维度。
 * 届满日以本段起期为基准推算（{@link #withGracePeriods}），天数 ≤0 或 null 时届满日为 null。
 * </p>
 *
 * @param periodType              保障期间类型（固定期限 / 终身 / 自定义）
 * @param periodStart             保障起期
 * @param periodEnd               保障止期（终身型为 null）
 * @param periodValue             期限数值（如 20 表示 20 年 / 至 70 岁的年数，终身型为 null）
 * @param periodUnit              期限单位（年 / 月 / 日）
 * @param waitingPeriodDays       本段等待期天数（0 表示无等待期）
 * @param waitingPeriodEndDate    等待期届满日（本段起期 + 等待期天数；无等待期时为 null）
 * @param hesitationPeriodDays    本段犹豫期天数（0 表示无犹豫期）
 * @param hesitationPeriodEndDate 犹豫期届满日（本段起期 + 犹豫期天数；无犹豫期时为 null）
 */
public record LineCoveragePeriod(CoveragePeriodType periodType, LocalDateTime periodStart, LocalDateTime periodEnd,
                                 Integer periodValue, PeriodUnit periodUnit,
                                 int waitingPeriodDays, LocalDateTime waitingPeriodEndDate,
                                 int hesitationPeriodDays, LocalDateTime hesitationPeriodEndDate) {

    /**
     * 构建固定期限保障期间（等待期/犹豫期留空，由 {@link #withGracePeriods} 追加）。
     *
     * @param start       保障起期
     * @param end         保障止期
     * @param periodValue 期限数值
     * @param periodUnit  期限单位
     * @return 固定期限保障期间
     */
    public static LineCoveragePeriod fixedTerm(LocalDateTime start, LocalDateTime end, Integer periodValue,
                                               PeriodUnit periodUnit) {
        return new LineCoveragePeriod(CoveragePeriodType.FIXED_TERM, start, end, periodValue, periodUnit,
                0, null, 0, null);
    }

    /**
     * 构建终身保障期间（无止期；等待期/犹豫期留空，由 {@link #withGracePeriods} 追加）。
     *
     * @param start 保障起期
     * @return 终身保障期间
     */
    public static LineCoveragePeriod wholeLife(LocalDateTime start) {
        return new LineCoveragePeriod(CoveragePeriodType.WHOLE_LIFE, start, null, null, null, 0, null, 0, null);
    }

    /**
     * 以本段起期为基准追加等待期/犹豫期（天数 null 或 ≤0 视为无，届满日记为 null）。
     * <p>
     * 🔴 天数与届满日的推算内聚于此（与 {@code PolicyPeriod.of} 原「工厂内聚推算规则」同范式）；
     * 起期为空（历史事件）时段级期间本身无效，届满日一并留空而不是拿别处时点顶替。
     * </p>
     *
     * @param waitingPeriodDays    本段等待期天数（可空）
     * @param hesitationPeriodDays 本段犹豫期天数（可空）
     * @return 追加等待期/犹豫期后的新实例
     */
    public LineCoveragePeriod withGracePeriods(Integer waitingPeriodDays, Integer hesitationPeriodDays) {
        int waiting = waitingPeriodDays != null && waitingPeriodDays > 0 ? waitingPeriodDays : 0;
        int hesitation = hesitationPeriodDays != null && hesitationPeriodDays > 0 ? hesitationPeriodDays : 0;
        LocalDateTime waitingEnd = waiting > 0 && periodStart != null ? periodStart.plusDays(waiting) : null;
        LocalDateTime hesitationEnd = hesitation > 0 && periodStart != null ? periodStart.plusDays(hesitation) : null;
        return new LineCoveragePeriod(periodType, periodStart, periodEnd, periodValue, periodUnit,
                waiting, waitingEnd, hesitation, hesitationEnd);
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
     * 指定时点是否处于本段等待期内（疾病类责任此期间不赔）。
     * <p>
     * 段级下沉自 {@code PolicyPeriod}（G11/g11-04，D2）——语义不变，判据基准由保单起期改为本段起期。
     * </p>
     *
     * @param at 判定时点
     * @return 在本段等待期内返回 {@code true}
     */
    public boolean isInWaitingPeriod(LocalDateTime at) {
        if (waitingPeriodDays <= 0 || at == null || periodStart == null || waitingPeriodEndDate == null) {
            return false;
        }
        return !at.isBefore(periodStart) && at.isBefore(waitingPeriodEndDate);
    }

    /**
     * 指定时点是否处于本段犹豫期内（可无条件退保）。
     *
     * @param at 判定时点
     * @return 在本段犹豫期内返回 {@code true}
     */
    public boolean isInHesitationPeriod(LocalDateTime at) {
        if (hesitationPeriodDays <= 0 || at == null || periodStart == null || hesitationPeriodEndDate == null) {
            return false;
        }
        return !at.isBefore(periodStart) && at.isBefore(hesitationPeriodEndDate);
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
