package com.titanium.policy.query.result;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 保单保险责任查询结果（L4，读侧对外契约）
 * <p>
 * 理赔域定责的数据来源：责任保额、免赔、赔付比例、责任级等待期均取出单时点冻结的快照，
 * 条款域后续改版不影响存量保单的赔付口径。
 * </p>
 * <p>
 * {@link #attachLevel} 决定保额计算基数：{@code SUBJECT} 按标的逐个计算（车损险、身故金），
 * {@code LINE} 在险种段内共享（三者险、医疗险年度累计额度）。
 * </p>
 */
@Data
public class PolicyCoverageQueryResult {

    /** 保单ID */
    private String     policyId;

    /** 所属险种段ID */
    private String     policyProductId;

    /** 来源条款ID */
    private String     clauseId;

    /** 来源条款版本（签发即冻结） */
    private String     clauseVersion;

    /** 责任编码 */
    private String     coverageCode;

    /** 责任名称 */
    private String     coverageName;

    /** 责任类型码 */
    private String     coverageType;

    /** 挂载层级码（LINE 段级 / SUBJECT 标的级） */
    private String     attachLevel;

    /** 挂载对象ID（LINE 指向段ID，SUBJECT 指向标的ID） */
    private String     attachRefId;

    /** 责任保额（赔付上限） */
    private BigDecimal coverageSumInsured;

    /** 赔付比例（1.0 表示 100%） */
    private BigDecimal indemnityRatio;

    /** 免赔类型码 */
    private String     deductibleType;

    /** 免赔额 */
    private BigDecimal deductibleAmount;

    /** 免赔比例 */
    private BigDecimal deductibleRatio;

    /** 责任级等待期天数（0 表示无等待期） */
    private Integer    waitingPeriodDays;

    /** 赔付规则摘要 */
    private String     payoutRuleSummary;
}
