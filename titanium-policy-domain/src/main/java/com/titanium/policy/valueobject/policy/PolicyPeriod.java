package com.titanium.policy.valueobject.policy;

import java.time.LocalDateTime;

/**
 * 保单期间值对象（保障起止期）
 * <p>
 * 承载保单级的保障起止时间边界，取代散落在 {@code PolicyBasicInfo} 的起止期裸字段。
 * </p>
 * <p>
 * 🔴 <b>等待期/犹豫期已自 G11/g11-04（D2 裁决）下沉至段级</b>：二者随
 * {@link LineCoveragePeriod}（险种段保障期间）按<b>段</b>承载并随 {@code PolicyProduct} 快照冻结，
 * 本值对象<b>不再持有</b>该两维度——同一保单下各段取值本就不同（交强险无等待期、商业车险另有配置），
 * 保单级单值表达不了，强行保留会让「段级真值」与「保单级冗余值」两处漂移。
 * 语义说明与判据（{@code isInWaitingPeriod}/{@code isInHesitationPeriod}）随之迁至段级。
 * </p>
 *
 * @param insurancePeriodStart 保障起期
 * @param insurancePeriodEnd   保障止期（终身型可为 null）
 */
public record PolicyPeriod(LocalDateTime insurancePeriodStart, LocalDateTime insurancePeriodEnd) {

    /**
     * 依保障起止期构建保单期间。
     *
     * @param start 保障起期
     * @param end   保障止期（终身型传 null）
     * @return 保单期间
     */
    public static PolicyPeriod of(LocalDateTime start, LocalDateTime end) {
        return new PolicyPeriod(start, end);
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
     * 保障起期是否已到达。
     *
     * @param at 判定时点
     * @return 起期已到返回 {@code true}
     */
    public boolean hasStarted(LocalDateTime at) {
        return insurancePeriodStart != null && at != null && !at.isBefore(insurancePeriodStart);
    }
}
