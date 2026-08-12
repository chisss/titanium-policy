package com.titanium.policy.query.view;

import java.math.BigDecimal;

import com.titanium.common.jpa.BaseView;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 保单保险责任读模型实体（L4，CQRS 读侧）
 * <p>
 * <b>理赔定责的唯一依据</b>：责任保额、免赔、赔付比例、责任级等待期在保单签发时冻结，
 * 条款域后续改版不影响存量保单。
 * </p>
 * <p>
 * 🔴 {@link #attachLevel} 决定保额与免赔的计算基数：
 * </p>
 * <ul>
 *   <li>{@code SUBJECT}：赔付对象是标的自身（车损险赔本车、寿险身故金赔被保险人），
 *       {@link #attachRefId} 指向标的ID，保额按标的逐个计算；</li>
 *   <li>{@code LINE}：赔付对象是第三方或额度段内共享（三者险、责任险、医疗险年度累计保额），
 *       {@link #attachRefId} 指向险种段ID，保额在段层面共享。</li>
 * </ul>
 * <p>
 * 由 {@code PolicyCoverageProjectionEventHandler} 从险种段内 {@code coverageSnapshots} 拆解投影，
 * 映射 {@code t_policy_coverage}。
 * </p>
 */
@Entity
@Table(name = "t_policy_coverage", indexes = {
        @Index(name = "idx_policy_coverage_policy", columnList = "policy_id, tenant_id"),
        @Index(name = "idx_policy_coverage_line", columnList = "policy_product_id"),
        @Index(name = "idx_policy_coverage_attach", columnList = "attach_level, attach_ref_id"),
        @Index(name = "idx_policy_coverage_tenant", columnList = "tenant_id")
})
@Getter
@Setter
public class PolicyCoverageView extends BaseView {

    /** 主键（policyId + 段ID + 责任编码派生，保证投影幂等） */
    @Id
    @Column(name = "id", nullable = false, length = 128)
    private String     id;

    /** 保单ID */
    @Column(name = "policy_id", nullable = false, length = 36)
    private String     policyId;

    /** 所属险种段ID */
    @Column(name = "policy_product_id", nullable = false, length = 64)
    private String     policyProductId;

    /** 来源条款ID */
    @Column(name = "clause_id", length = 36)
    private String     clauseId;

    /** 来源条款版本（签发即冻结） */
    @Column(name = "clause_version", length = 32)
    private String     clauseVersion;

    /** 责任编码（快照） */
    @Column(name = "coverage_code", length = 64)
    private String     coverageCode;

    /** 责任名称（快照） */
    @Column(name = "coverage_name", length = 256)
    private String     coverageName;

    /** 责任类型码（MEDICAL/DEATH/CRITICAL_ILLNESS/ACCIDENT） */
    @Column(name = "coverage_type", length = 50)
    private String     coverageType;

    /** 挂载层级码（LINE 段级 / SUBJECT 标的级） */
    @Column(name = "attach_level", nullable = false, length = 16)
    private String     attachLevel;

    /** 挂载对象ID（LINE 指向段ID，SUBJECT 指向标的ID） */
    @Column(name = "attach_ref_id", length = 64)
    private String     attachRefId;

    /** 责任保额（该责任的赔付上限） */
    @Column(name = "coverage_sum_insured", precision = 18, scale = 2)
    private BigDecimal coverageSumInsured;

    /** 赔付比例（1.0 表示 100% 报销/给付） */
    @Column(name = "indemnity_ratio", precision = 5, scale = 4)
    private BigDecimal indemnityRatio;

    /** 免赔类型码（NONE/FIXED_AMOUNT/PROPORTIONAL） */
    @Column(name = "deductible_type", length = 32)
    private String     deductibleType;

    /** 免赔额（固定金额免赔时使用） */
    @Column(name = "deductible_amount", precision = 18, scale = 2)
    private BigDecimal deductibleAmount;

    /** 免赔比例（比例免赔时使用） */
    @Column(name = "deductible_ratio", precision = 5, scale = 4)
    private BigDecimal deductibleRatio;

    /** 责任级等待期天数（0 表示无等待期；区别于保单级等待期） */
    @Column(name = "waiting_period_days")
    private Integer    waitingPeriodDays;

    /** 赔付规则摘要（详细规则在条款域） */
    @Column(name = "payout_rule_summary", length = 512)
    private String     payoutRuleSummary;
}
