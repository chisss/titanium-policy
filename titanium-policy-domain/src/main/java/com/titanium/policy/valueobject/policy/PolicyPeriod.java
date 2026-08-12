package com.titanium.policy.valueobject.policy;

import java.time.LocalDateTime;

/**
 * 保单期间值对象（保障期 + 等待期 + 犹豫期）
 * <p>
 * 承载保单级的三类时间边界，取代散落在 {@code PolicyBasicInfo} 的起止期裸字段，并补齐此前
 * <b>policy 域完全缺失</b>的等待期与犹豫期（product 侧 {@code InsureCondition.waitingPeriodDays}
 * / {@code hesitationPeriodDays} 早有配置但保单侧零引用）。
 * </p>
 * <p>
 * 三者的业务语义与差异：
 * </p>
 * <ul>
 *   <li><b>保障期</b>：保险责任的有效区间，出险须落在此区间内。</li>
 *   <li><b>等待期（Waiting Period）</b>：保单生效后的一段时间内，特定风险（疾病类）不予承保。
 *       医疗险常见 30 天、重疾险 90-180 天。意外险通常无等待期。</li>
 *   <li><b>犹豫期（Free-Look Period）</b>：保单生效后 10-20 天内投保人可无条件退保，仅扣工本费。
 *       长期险法定具备，短期险通常无。</li>
 * </ul>
 * <p>
 * 责任级等待期（如「一般医疗 30 天、恶性肿瘤 90 天」）在 {@link CoverageSnapshot#waitingPeriodDays}
 * 上另行承载，本值对象承载保单级缺省等待期。
 * </p>
 *
 * @param insurancePeriodStart    保障起期
 * @param insurancePeriodEnd      保障止期（终身型可为 null）
 * @param waitingPeriodDays       等待期天数（0 表示无等待期）
 * @param waitingPeriodEndDate    等待期届满日（起期 + 等待期天数；无等待期时等于起期）
 * @param hesitationPeriodDays    犹豫期天数（0 表示无犹豫期）
 * @param hesitationPeriodEndDate 犹豫期届满日（起期 + 犹豫期天数；无犹豫期时等于起期）
 */
public record PolicyPeriod(LocalDateTime insurancePeriodStart, LocalDateTime insurancePeriodEnd, int waitingPeriodDays,
                           LocalDateTime waitingPeriodEndDate, int hesitationPeriodDays,
                           LocalDateTime hesitationPeriodEndDate) {

    /**
     * 依保障起止期与产品配置的等待期/犹豫期天数构建，届满日由起期推算（工厂内聚推算规则）。
     *
     * @param start                保障起期
     * @param end                  保障止期（终身型传 null）
     * @param waitingPeriodDays    等待期天数（null 视为 0）
     * @param hesitationPeriodDays 犹豫期天数（null 视为 0）
     * @return 保单期间
     */
    public static PolicyPeriod of(LocalDateTime start, LocalDateTime end, Integer waitingPeriodDays,
                                  Integer hesitationPeriodDays) {
        int waiting = waitingPeriodDays != null && waitingPeriodDays > 0 ? waitingPeriodDays : 0;
        int hesitation = hesitationPeriodDays != null && hesitationPeriodDays > 0 ? hesitationPeriodDays : 0;
        LocalDateTime waitingEnd = start != null ? start.plusDays(waiting) : null;
        LocalDateTime hesitationEnd = start != null ? start.plusDays(hesitation) : null;
        return new PolicyPeriod(start, end, waiting, waitingEnd, hesitation, hesitationEnd);
    }

    /**
     * 指定时点是否处于保障期内。
     * <p>
     * 止期为空（终身型）时仅校验起期已到。
     * </p>
     *
     * @param at 判定时点
     * @return 在保障期内返回 {@code true}
     */
    public boolean isCoverageActive(LocalDateTime at) {
        if (at == null || insurancePeriodStart == null || at.isBefore(insurancePeriodStart)) {
            return false;
        }
        return insurancePeriodEnd == null || !at.isAfter(insurancePeriodEnd);
    }

    /**
     * 指定时点是否处于等待期内（疾病类责任此期间不赔）。
     *
     * @param at 判定时点
     * @return 在等待期内返回 {@code true}
     */
    public boolean isInWaitingPeriod(LocalDateTime at) {
        if (waitingPeriodDays <= 0 || at == null || waitingPeriodEndDate == null) {
            return false;
        }
        return !at.isBefore(insurancePeriodStart) && at.isBefore(waitingPeriodEndDate);
    }

    /**
     * 指定时点是否处于犹豫期内（可无条件退保）。
     *
     * @param at 判定时点
     * @return 在犹豫期内返回 {@code true}
     */
    public boolean isInHesitationPeriod(LocalDateTime at) {
        if (hesitationPeriodDays <= 0 || at == null || hesitationPeriodEndDate == null) {
            return false;
        }
        return !at.isBefore(insurancePeriodStart) && at.isBefore(hesitationPeriodEndDate);
    }

    /**
     * 保障起期是否已到达。
     *
     * @param at 判定时点
     * @return 起期已到返回 {@code true}
     */
    public boolean hasStarted(LocalDateTime at) {
        return insurancePeriodStart != null && at != null && !at.isBefore(insurancePeriodStart);
    }
}
