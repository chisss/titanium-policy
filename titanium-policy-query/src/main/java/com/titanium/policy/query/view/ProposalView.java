package com.titanium.policy.query.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.common.jpa.BaseView;
import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.SalesChannel;
import com.titanium.policy.valueobject.proposal.ProposalStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 投保意向单读模型实体（CQRS Projection）
 * <p>
 * 对应读模型表 {@code t_proposal_view}，与写侧聚合根持久化表 {@code t_proposal} 物理隔离。 由
 * {@link com.titanium.policy.query.handler.projection.ProposalProjectionEventHandler} 订阅领域事件投影而来，
 * 仅供查询侧使用，禁止写侧逻辑直接操作。
 * </p>
 * <p>
 * 继承 {@link BaseView}，复用租户ID、创建/更新时间（投影时间）、乐观锁版本等读模型公共字段。
 * 状态直接采用领域状态枚举 {@link ProposalStatus.StatusCode}。申请人/标的明细不进领域事件，读模型仅投影 basicInfo 级数据。
 * </p>
 */
@Entity
@Table(name = "t_proposal_view")
@Getter
@Setter
public class ProposalView extends BaseView {

    /** 意向单唯一标识（聚合根ID，读模型主键） */
    @Id
    @Column(name = "proposal_id", nullable = false, length = 36)
    private String                    proposalId;

    /** 意向单编号（高频查询字段） */
    @Column(name = "proposal_no", nullable = false, length = 64)
    private String                    proposalNo;

    /** 保单形态 */
    @Enumerated(EnumType.STRING)
    @Column(name = "policy_form", length = 32)
    private PolicyForm                policyForm;

    /** 销售渠道 */
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 32)
    private SalesChannel              channel;

    /** 客户ID */
    @Column(name = "customer_id", length = 36)
    private String                    customerId;

    /** 意向保额 */
    @Column(name = "intended_sum_insured", precision = 18, scale = 2)
    private BigDecimal                intendedSumInsured;

    /** 意向保费 */
    @Column(name = "intended_premium", precision = 18, scale = 2)
    private BigDecimal                intendedPremium;

    /** 保险起期 */
    @Column(name = "insurance_period_start")
    private LocalDateTime             insurancePeriodStart;

    /** 保险止期 */
    @Column(name = "insurance_period_end")
    private LocalDateTime             insurancePeriodEnd;

    /** 期望险种编码 */
    @Column(name = "expected_product_code", length = 64)
    private String                    expectedProductCode;

    /** 险种三级分类（源头捕获，可空以兼容存量数据） */
    @Enumerated(EnumType.STRING)
    @Column(name = "insurance_type", length = 64)
    private InsuranceProductType      insuranceType;

    /** 意向单状态（领域状态机编码） */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_code", nullable = false, length = 32)
    private ProposalStatus.StatusCode status;
}
