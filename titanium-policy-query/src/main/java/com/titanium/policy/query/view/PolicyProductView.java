package com.titanium.policy.query.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.common.jpa.BaseView;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 保单险种段读模型实体（L2，CQRS 读侧）
 * <p>
 * 一张保单对应 1..N 行——这是「一单多险」在读侧的落地：寿险的「终身寿主险 + 附加重疾 + 附加医疗」、
 * 车险的「交强险 + 车损险 + 三者险」各自一行，每行持有独立的保额、保费、保障期间、缴费条件、
 * 核保结论与承保状态。
 * </p>
 * <p>
 * 由 {@code PolicyProductProjectionEventHandler} 从 {@code PolicyCreatedEvent.policyProducts}
 * 拆解投影，映射 {@code t_policy_product}。枚举类字段以 code 字符串存储（读侧对外稳定，
 * 不因枚举重构而破坏存量数据）。
 * </p>
 */
@Entity
@Table(name = "t_policy_product", indexes = {
        @Index(name = "idx_policy_product_policy", columnList = "policy_id, tenant_id"),
        @Index(name = "idx_policy_product_product", columnList = "product_id, tenant_id"),
        @Index(name = "idx_policy_product_tenant", columnList = "tenant_id")
})
@Getter
@Setter
public class PolicyProductView extends BaseView {

    /** 主键（policyId + 段序号派生，保证投影幂等） */
    @Id
    @Column(name = "id", nullable = false, length = 128)
    private String        id;

    /** 保单ID */
    @Column(name = "policy_id", nullable = false, length = 36)
    private String        policyId;

    /** 险种段ID（保单内唯一） */
    @Column(name = "policy_product_id", nullable = false, length = 64)
    private String        policyProductId;

    /** 段序号（对应出单请求的 planLine 序号） */
    @Column(name = "line_no", nullable = false)
    private Integer       lineNo;

    /** 产品类别码（MAIN 主险 / RIDER 附加险） */
    @Column(name = "product_category", nullable = false, length = 16)
    private String        productCategory;

    /** 依附的主险段ID（RIDER 必填，MAIN 为空） */
    @Column(name = "parent_policy_product_id", length = 64)
    private String        parentPolicyProductId;

    /** 产品ID（指向 product 域） */
    @Column(name = "product_id", length = 36)
    private String        productId;

    /** 产品编码（快照） */
    @Column(name = "product_code", length = 64)
    private String        productCode;

    /** 产品名称（快照） */
    @Column(name = "product_name", length = 256)
    private String        productName;

    /** 产品版本（快照，锁定出单时点的产品定义） */
    @Column(name = "product_version", length = 32)
    private String        productVersion;

    /** 定价计划版本（确认保费时冻结；历史保单可为空） */
    @Column(name = "pricing_plan_version", length = 64)
    private String        pricingPlanVersion;

    /** 险种三级分类码 */
    @Column(name = "insurance_type", length = 64)
    private String        insuranceType;

    /** 本险种保额（独立于其他段） */
    @Column(name = "sum_insured", precision = 18, scale = 2)
    private BigDecimal    sumInsured;

    /** 本险种保费（独立；拒保段不计入保单总保费） */
    @Column(name = "premium", precision = 18, scale = 2)
    private BigDecimal    premium;

    /** 币种（ISO 4217） */
    @Column(name = "currency", length = 8)
    private String        currency;

    /** 本险种保障起期（可独立于保单主期间） */
    @Column(name = "period_start")
    private LocalDateTime periodStart;

    /** 本险种保障止期（终身型为空） */
    @Column(name = "period_end")
    private LocalDateTime periodEnd;

    /** 保障期间类型码（FIXED_TERM/WHOLE_LIFE/CUSTOM） */
    @Column(name = "period_type", length = 32)
    private String        periodType;

    /** 本险种缴费频率码（缴费期 ≠ 保障期） */
    @Column(name = "payment_frequency", length = 32)
    private String        paymentFrequency;

    /** 本险种缴费年数 */
    @Column(name = "premium_payment_years")
    private Integer       premiumPaymentYears;

    /** 本险种核保结论码（ACCEPT/MODIFY/REJECT/POSTPONE） */
    @Column(name = "underwriting_conclusion", length = 32)
    private String        underwritingConclusion;

    /** 本险种承保状态码（UNDERWRITING/ACCEPTED/EFFECTIVE/REJECTED/SURRENDERED/EXPIRED） */
    @Column(name = "line_status", length = 32)
    private String        lineStatus;
}
