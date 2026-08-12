package com.titanium.policy.valueobject.policy;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.titanium.metadata.enums.clause.DeductibleType;
import com.titanium.metadata.enums.policy.CoverageAttachLevel;
import com.titanium.metadata.valueobject.Money;

/**
 * 保险责任快照值对象（L4）
 * <p>
 * 出单时点对条款域 {@code Coverage} 的不可变快照，是<b>理赔定责的唯一依据</b>：责任保额、免赔、
 * 赔付比例、责任级等待期在保单签发时冻结，条款后续改版不影响存量保单。
 * </p>
 * <p>
 * 🔴 <b>{@link #attachLevel} 决定保额与免赔的计算基数</b>（见 {@link CoverageAttachLevel}）：
 * </p>
 * <ul>
 *   <li>{@code SUBJECT}：赔付对象是标的自身（车损险赔本车、寿险身故金赔被保险人），
 *       {@code attachRefId} 指向 {@code InsuredSubject.subjectId}，保额按标的逐个计算；</li>
 *   <li>{@code LINE}：赔付对象是第三方或额度段内共享（三者险、责任险、医疗险年度累计保额），
 *       {@code attachRefId} 指向 {@code PolicyProduct.policyProductId}，保额在险种段层面共享。</li>
 * </ul>
 *
 * @param coverageId        责任快照ID（保单内唯一）
 * @param coverageCode      责任编码（快照，指向条款域责任）
 * @param coverageName      责任名称（快照）
 * @param coverageType      责任类型码（如 MEDICAL/DEATH/CRITICAL_ILLNESS，取条款域 CoverageType 的 code）
 * @param attachLevel       挂载层级（险种段级 / 标的级）
 * @param attachRefId       挂载对象ID（段级指向 policyProductId，标的级指向 subjectId）
 * @param coverageSumInsured 责任保额（该责任的赔付上限）
 * @param indemnityRatio    赔付比例（1.0 表示 100% 报销/给付）
 * @param deductibleType    免赔类型（无免赔 / 固定金额 / 比例免赔）
 * @param deductibleAmount  免赔额（固定金额免赔时使用）
 * @param deductibleRatio   免赔比例（比例免赔时使用，0-1）
 * @param waitingPeriodDays 责任级等待期天数（0 表示无等待期；区别于保单级等待期）
 * @param payoutRuleSummary 赔付规则摘要（结构化规则的可读摘要，详规则在条款域）
 */
public record CoverageSnapshot(String coverageId, String coverageCode, String coverageName, String coverageType,
                               CoverageAttachLevel attachLevel, String attachRefId, Money coverageSumInsured,
                               BigDecimal indemnityRatio, DeductibleType deductibleType, BigDecimal deductibleAmount,
                               BigDecimal deductibleRatio, int waitingPeriodDays, String payoutRuleSummary) {

    /** 全额赔付比例（100%） */
    private static final BigDecimal FULL_RATIO = BigDecimal.ONE;

    /**
     * 是否挂在标的上（赔付对象为标的自身）。
     *
     * @return 标的级返回 {@code true}
     */
    @JsonIgnore
    public boolean isAttachedToSubject() {
        return attachLevel == CoverageAttachLevel.SUBJECT;
    }

    /**
     * 是否挂在险种段上（赔付对象为第三方或额度段内共享）。
     *
     * @return 段级返回 {@code true}
     */
    @JsonIgnore
    public boolean isAttachedToLine() {
        return attachLevel == CoverageAttachLevel.LINE;
    }

    /**
     * 判断本责任是否挂在指定对象上。
     *
     * @param level 挂载层级
     * @param refId 挂载对象ID
     * @return 挂载匹配返回 {@code true}
     */
    public boolean isAttachedTo(CoverageAttachLevel level, String refId) {
        return attachLevel == level && refId != null && refId.equals(attachRefId);
    }

    /**
     * 实际赔付比例（缺省视为全额赔付）。
     *
     * @return 赔付比例，未配置时返回 1.0
     */
    public BigDecimal effectiveIndemnityRatio() {
        return indemnityRatio != null ? indemnityRatio : FULL_RATIO;
    }

    /**
     * 本责任是否设有免赔。
     *
     * @return 存在免赔返回 {@code true}
     */
    public boolean hasDeductible() {
        return deductibleType != null && deductibleType != DeductibleType.NONE;
    }

    /**
     * 本责任是否设有等待期。
     *
     * @return 等待期天数为正返回 {@code true}
     */
    public boolean hasWaitingPeriod() {
        return waitingPeriodDays > 0;
    }
}
