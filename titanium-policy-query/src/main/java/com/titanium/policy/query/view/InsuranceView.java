package com.titanium.policy.query.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.common.jpa.BaseView;
import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;
import com.titanium.policy.valueobject.insurance.InsuranceStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 投保单读模型实体（CQRS Projection）
 * <p>
 * 对应读模型表 {@code t_insurance_view}，与写侧聚合根持久化表 {@code t_insurance} 物理隔离。 由
 * {@link com.titanium.policy.query.handler.projection.InsuranceProjectionEventHandler} 订阅领域事件投影而来，
 * 仅供查询侧使用，禁止写侧逻辑直接操作。
 * </p>
 * <p>
 * 继承 {@link BaseView}，复用租户ID、创建/更新时间（投影时间）、乐观锁版本等读模型公共字段。
 * 状态直接采用领域状态枚举 {@link InsuranceStatus.StatusCode}（investment 域尚无 metadata 级投保单状态枚举）。
 * </p>
 */
@Entity
@Table(name = "t_insurance_view")
@Getter
@Setter
public class InsuranceView extends BaseView {

    /** 投保单唯一标识（聚合根ID，读模型主键） */
    @Id
    @Column(name = "insurance_id", nullable = false, length = 36)
    private String                   insuranceId;

    /** 投保单编号（高频查询字段） */
    @Column(name = "insurance_no", nullable = false, length = 64)
    private String                   insuranceNo;

    /** 关联意向单ID（直接创建时为空） */
    @Column(name = "proposal_id", length = 36)
    private String                   proposalId;

    /** 保单形态 */
    @Enumerated(EnumType.STRING)
    @Column(name = "policy_form", length = 32)
    private PolicyForm               policyForm;

    /** 险种三级分类 */
    @Enumerated(EnumType.STRING)
    @Column(name = "insurance_type", length = 64)
    private InsuranceProductType     insuranceType;

    /** 投保人ID */
    @Column(name = "holder_id", length = 36)
    private String                   holderId;

    /** 被保险人数 */
    @Column(name = "insured_count")
    private Integer                  insuredCount;

    /** 精确保费金额 */
    @Column(name = "exact_premium", precision = 18, scale = 2)
    private BigDecimal               exactPremium;

    /** 币种（ISO 4217） */
    @Column(name = "currency", length = 8)
    private String                   currency;

    /** 保险起期 */
    @Column(name = "insurance_period_start")
    private LocalDateTime            insurancePeriodStart;

    /** 保险止期 */
    @Column(name = "insurance_period_end")
    private LocalDateTime            insurancePeriodEnd;

    /** 投保单状态（领域状态机编码） */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_code", nullable = false, length = 32)
    private InsuranceStatus.StatusCode status;

    /** 核保结论（核保结果回流后填充） */
    @Enumerated(EnumType.STRING)
    @Column(name = "underwriting_result_code", length = 32)
    private ConclusionType           underwritingResultCode;

    /** 核保单号 */
    @Column(name = "underwriting_id", length = 36)
    private String                   underwritingId;

    /** 承保时间 */
    @Column(name = "issued_time")
    private LocalDateTime            issuedTime;
}
