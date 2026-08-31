package com.titanium.policy.valueobject.product;

import java.math.BigDecimal;
import java.util.List;

import com.titanium.metadata.enums.insurance.SubjectType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.PaymentFrequency;
import com.titanium.metadata.enums.product.ProductEnum.PeriodUnit;
import com.titanium.metadata.enums.product.ProductEnum.UnderwritingMode;

/**
 * 产品投保规则值对象（防腐）
 * <p>
 * 聚合产品域的五类出单期必需配置：投保条件、保障期间选项、缴费选项、保单形态、核保模式。
 * 它们在出单受理阶段被同一个校验环节消费（{@code IssuanceEligibilityDomainService}），
 * 分散取数会放大远程调用开销，故一次取全。
 * </p>
 * <p>
 * 🔴 <b>为何放 valueobject 而非 Port 内嵌</b>：本值对象是<b>领域服务的入参</b>——
 * {@code IssuanceEligibilityDomainService} 依它裁决投保要素。若定义为 {@code ProductServicePort}
 * 的内嵌 record，领域服务就必须 import Port 所在包，触发 ArchUnit 第 8 条断言
 * （{@code domain.service} 不得依赖 {@code domain.port}）。该断言的意图是「领域服务不得取数」，
 * 而入参值对象本身不是取数能力——把值对象移出 Port 包即可同时满足断言与设计意图：
 * 取数由 application 经 Port 完成，裁决由领域服务对值对象进行。
 * </p>
 *
 * @param minAge                      最小投保年龄
 * @param maxAge                      最大投保年龄
 * @param minSumInsured               最小保额
 * @param maxSumInsured               最大保额
 * @param maxInsuredCount             最大被保险人数
 * @param minGroupSize                团险最小参保人数
 * @param forbiddenOccupations        禁止职业类别
 * @param allowedRegions              允许地域（空表示不限）
 * @param forbiddenRegions            禁止地域
 * @param waitingPeriodDays           等待期天数
 * @param hesitationPeriodDays        犹豫期天数
 * @param policyForm                  保单形态
 * @param beneficiaryRequired         是否必须指定受益人
 * @param allowedFrequencies          允许的缴费频率
 * @param allowedPaymentTerms         允许的缴费期限（年数选项）
 * @param coveragePeriodUnit          保障期间单位
 * @param fixedTermOptions            固定期限选项（年数）
 * @param underwritingMode            核保模式
 * @param manualReviewAmountThreshold 转人工核保的保额阈值
 * @param underwritingSkippable       是否可跳过核保
 * @param prepaymentRequired          是否必须先收费再出单
 */
public record ProductIssueRules(Integer minAge, Integer maxAge, BigDecimal minSumInsured, BigDecimal maxSumInsured,
                                Integer maxInsuredCount, Integer minGroupSize, List<String> forbiddenOccupations,
                                List<String> allowedRegions, List<String> forbiddenRegions, Integer waitingPeriodDays,
                                Integer hesitationPeriodDays, PolicyForm policyForm, boolean beneficiaryRequired,
                                List<PaymentFrequency> allowedFrequencies, List<Integer> allowedPaymentTerms,
                                PeriodUnit coveragePeriodUnit, List<Integer> fixedTermOptions,
                                UnderwritingMode underwritingMode, BigDecimal manualReviewAmountThreshold,
                                boolean underwritingSkippable, boolean prepaymentRequired,
                                SubjectType subjectType, List<String> requiredSubjectAttributes) {

    /**
     * 兼容旧版调用方的构造器。未提供标的 Schema 时保持原有“无段级属性约束”语义。
     */
    public ProductIssueRules(Integer minAge, Integer maxAge, BigDecimal minSumInsured, BigDecimal maxSumInsured,
                             Integer maxInsuredCount, Integer minGroupSize, List<String> forbiddenOccupations,
                             List<String> allowedRegions, List<String> forbiddenRegions, Integer waitingPeriodDays,
                             Integer hesitationPeriodDays, PolicyForm policyForm, boolean beneficiaryRequired,
                             List<PaymentFrequency> allowedFrequencies, List<Integer> allowedPaymentTerms,
                             PeriodUnit coveragePeriodUnit, List<Integer> fixedTermOptions,
                             UnderwritingMode underwritingMode, BigDecimal manualReviewAmountThreshold,
                             boolean underwritingSkippable, boolean prepaymentRequired) {
        this(minAge, maxAge, minSumInsured, maxSumInsured, maxInsuredCount, minGroupSize, forbiddenOccupations,
                allowedRegions, forbiddenRegions, waitingPeriodDays, hesitationPeriodDays, policyForm,
                beneficiaryRequired, allowedFrequencies, allowedPaymentTerms, coveragePeriodUnit, fixedTermOptions,
                underwritingMode, manualReviewAmountThreshold, underwritingSkippable, prepaymentRequired, null,
                List.of());
    }

    public boolean requiresSubjectAttribute(String attribute) {
        return attribute != null && requiredSubjectAttributes != null && requiredSubjectAttributes.contains(attribute);
    }

    /**
     * 指定缴费频率是否在产品允许集内。
     *
     * @param frequency 缴费频率
     * @return 允许返回 {@code true}（未配置允许集时不限制）
     */
    public boolean allowsFrequency(PaymentFrequency frequency) {
        return frequency == null || isUnrestricted(allowedFrequencies) || allowedFrequencies.contains(frequency);
    }

    /**
     * 指定缴费年数是否在产品允许集内。
     *
     * @param years 缴费年数
     * @return 允许返回 {@code true}（未配置允许集时不限制）
     */
    public boolean allowsPaymentTerm(Integer years) {
        return years == null || isUnrestricted(allowedPaymentTerms) || allowedPaymentTerms.contains(years);
    }

    /**
     * 指定保障期限是否在产品固定期限选项内。
     *
     * @param periodValue 保障期限数值
     * @return 允许返回 {@code true}（未配置选项时不限制）
     */
    public boolean allowsCoveragePeriod(Integer periodValue) {
        return periodValue == null || isUnrestricted(fixedTermOptions) || fixedTermOptions.contains(periodValue);
    }

    /**
     * 投保年龄是否落在产品允许区间内（区间端点未配置视为该侧不限）。
     *
     * @param age 被保险人年龄
     * @return 落在区间内返回 {@code true}
     */
    public boolean allowsAge(Integer age) {
        return age == null || (!isBelow(age, minAge) && !isAbove(age, maxAge));
    }

    /**
     * 投保年龄是否低于产品最小投保年龄。
     *
     * @param age 被保险人年龄
     * @return 低于返回 {@code true}
     */
    public boolean isAgeBelowMin(Integer age) {
        return isBelow(age, minAge);
    }

    /**
     * 保额是否落在产品允许区间内。
     *
     * @param sumInsured 投保保额
     * @return 落在区间内返回 {@code true}
     */
    public boolean allowsSumInsured(BigDecimal sumInsured) {
        return sumInsured != null && !isBelow(sumInsured, minSumInsured) && !isAbove(sumInsured, maxSumInsured);
    }

    /**
     * 保额是否低于产品最小保额。
     *
     * @param sumInsured 投保保额
     * @return 低于返回 {@code true}
     */
    public boolean isSumInsuredBelowMin(BigDecimal sumInsured) {
        return isBelow(sumInsured, minSumInsured);
    }

    /**
     * 被保险人数是否落在产品允许区间内（团险有最小参保人数要求）。
     *
     * @param count 被保险人数
     * @return 落在区间内返回 {@code true}
     */
    public boolean allowsInsuredCount(int count) {
        return !isBelow(count, minGroupSize) && !isAbove(count, maxInsuredCount);
    }

    /**
     * 被保险人数是否低于产品最小参保人数。
     *
     * @param count 被保险人数
     * @return 低于返回 {@code true}
     */
    public boolean isInsuredCountBelowMin(int count) {
        return isBelow(count, minGroupSize);
    }

    /**
     * 职业是否在产品禁保清单外（未配置清单时不限制）。
     *
     * @param occupation 职业类别码
     * @return 允许承保返回 {@code true}
     */
    public boolean allowsOccupation(String occupation) {
        return occupation == null || isUnrestricted(forbiddenOccupations)
                || !forbiddenOccupations.contains(occupation);
    }

    /**
     * 地域是否可保：不在禁止清单内，且（若配置了允许清单）须在允许清单内。
     *
     * @param region 地域码
     * @return 可保返回 {@code true}
     */
    public boolean allowsRegion(String region) {
        return region == null || (!isRegionForbidden(region) && isRegionInAllowedList(region));
    }

    /**
     * 地域是否明确在禁保清单内。
     *
     * @param region 地域码
     * @return 在禁保清单内返回 {@code true}
     */
    public boolean isRegionForbidden(String region) {
        return region != null && !isUnrestricted(forbiddenRegions) && forbiddenRegions.contains(region);
    }

    /**
     * 该保额是否需转人工核保。
     *
     * @param sumInsured 投保保额
     * @return 达到阈值返回 {@code true}
     */
    public boolean requiresManualUnderwriting(BigDecimal sumInsured) {
        return !isBelow(sumInsured, manualReviewAmountThreshold) && sumInsured != null
                && manualReviewAmountThreshold != null;
    }

    /**
     * 地域是否落在允许清单内（未配置允许清单视为不限制）。
     */
    private boolean isRegionInAllowedList(String region) {
        return isUnrestricted(allowedRegions) || allowedRegions.contains(region);
    }

    /**
     * 清单是否未配置约束（null 或空集视为不限制）。
     */
    private boolean isUnrestricted(List<?> restriction) {
        return restriction == null || restriction.isEmpty();
    }

    /**
     * 数值是否低于下界（下界未配置视为该侧不限）。
     */
    private boolean isBelow(Number value, Number lowerBound) {
        return value != null && lowerBound != null && compare(value, lowerBound) < 0;
    }

    /**
     * 数值是否高于上界（上界未配置视为该侧不限）。
     */
    private boolean isAbove(Number value, Number upperBound) {
        return value != null && upperBound != null && compare(value, upperBound) > 0;
    }

    /**
     * 数值比较（统一以 BigDecimal 比较，兼容 Integer 与 BigDecimal 混用）。
     */
    private int compare(Number left, Number right) {
        return toDecimal(left).compareTo(toDecimal(right));
    }

    /**
     * 数值 → BigDecimal（保留 BigDecimal 原精度）。
     */
    private BigDecimal toDecimal(Number value) {
        return value instanceof BigDecimal decimal ? decimal : BigDecimal.valueOf(value.doubleValue());
    }
}
