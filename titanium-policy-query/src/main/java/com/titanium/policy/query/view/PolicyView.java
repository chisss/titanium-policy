package com.titanium.policy.query.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.common.jpa.BaseView;
import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyEnum;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 保单读模型实体（CQRS Projection）
 * <p>
 * 对应读模型表 {@code t_policy_view}，与写侧聚合根持久化表 {@code t_policy} 物理隔离。 由
 * {@link com.titanium.policy.query.handler.projection.PolicyProjectionEventHandler} 订阅领域事件投影而来，
 * 仅供查询侧使用，禁止写侧逻辑直接操作。
 * </p>
 * <p>
 * 继承 {@link BaseView}，复用租户ID、创建/更新时间（投影时间）、乐观锁版本等读模型公共字段。
 * </p>
 * <p>
 * <b>字段填充说明</b>：字段值来源于领域事件。部分业务字段（如投保人姓名、险种名称）当前领域事件 未携带，投影时留空，后续可通过事件增强或跨域事件补全。
 * </p>
 */
@Entity
@Table(name = "t_policy_view")
@Getter
@Setter
public class PolicyView extends BaseView {

    /** 保单唯一标识（聚合根ID，读模型主键） */
    @Id
    @Column(name = "policy_id", nullable = false, length = 36)
    private String        policyId;

    /** 保单号（高频查询字段） */
    @Column(name = "policy_no", nullable = false, length = 64)
    private String        policyNo;

    /** 关联投保单ID */
    @Column(name = "insurance_id", length = 36)
    private String        insuranceId;

    /** 保单状态（来源 PolicyStatus.StatusCode 名称） */
    @Enumerated(EnumType.STRING)
    @Column(name = "policy_status", nullable = false, length = 32)
    private PolicyEnum.PolicyStatus policyStatus;

    /** 保费金额 */
    @Column(name = "premium", precision = 18, scale = 2)
    private BigDecimal    premium;

    /** 币种（ISO 4217） */
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", length = 8)
    private CurrencyEnum  currency;

    /** 保险起期 */
    @Column(name = "start_date")
    private LocalDateTime startDate;

    /** 保险止期 */
    @Column(name = "end_date")
    private LocalDateTime endDate;

    /** 签发时间 */
    @Column(name = "issue_time")
    private LocalDateTime issueTime;

    /** 险种三级分类 */
    @Enumerated(EnumType.STRING)
    @Column(name = "insurance_type", length = 64)
    private InsuranceProductType insuranceType;

    /** 投保人ID（事件暂未携带，预留） */
    @Column(name = "policy_holder_id", length = 36)
    private String        policyHolderId;

    /** 投保人姓名（事件暂未携带，预留） */
    @Column(name = "policy_holder_name", length = 128)
    private String        policyHolderName;

    /** 投保人证件类型快照 */
    @Column(name = "policy_holder_id_type", length = 32)
    private String        policyHolderIdType;

    /** 投保人证件号码快照 */
    @Column(name = "policy_holder_id_no", length = 64)
    private String        policyHolderIdNo;

    /** 投保人手机号快照 */
    @Column(name = "policy_holder_phone", length = 32)
    private String        policyHolderPhone;

    /** 被保险人姓名（事件暂未携带，预留） */
    @Column(name = "insured_name", length = 128)
    private String        insuredName;

    /** 险种编码（事件暂未携带，预留） */
    @Column(name = "product_code", length = 64)
    private String        productCode;

    /** 保单当前业务版本号（批改后递增；区别于基类乐观锁 version） */
    @Column(name = "current_version")
    private Integer       currentVersion;

    /** 满期给付金额（两全/生存险满期给付后填充） */
    @Column(name = "maturity_benefit", precision = 18, scale = 2)
    private BigDecimal    maturityBenefit;

    /** 是否已豁免后续保费（投保人身故/全残豁免后为 true，保单持续有效） */
    @Column(name = "premium_waived")
    private Boolean       premiumWaived;

    /** 保费豁免原因码（DISABILITY/DEATH/CRITICAL_ILLNESS，未豁免为空） */
    @Column(name = "waiver_reason", length = 32)
    private String        waiverReason;

    /** 累计已派发红利（分红险红利派发累积，非分红险为空） */
    @Column(name = "accumulated_dividend", precision = 18, scale = 2)
    private BigDecimal    accumulatedDividend;

    /** 红利领取方式码（CASH/PAID_UP_ADDITION/ACCUMULATE/OFFSET_PREMIUM，非分红险为空） */
    @Column(name = "dividend_option", length = 32)
    private String        dividendOption;

    /** 关联投资账户ID（投连/万能保单挂接投资账户，非投连类为空） */
    @Column(name = "investment_account_id", length = 64)
    private String        investmentAccountId;

    /** 投资账户最新价值（investment 域回写，展示型最终一致数据，非投连类为空） */
    @Column(name = "investment_account_value", precision = 18, scale = 2)
    private BigDecimal    investmentAccountValue;

    // ==================== 一单多险与收费/渠道/期间（本期新增） ====================

    /** 关联意向单ID（三步出单来源；支撑 Proposal→Insurance→Policy 三级贯通查询） */
    @Column(name = "proposal_id", length = 36)
    private String        proposalId;

    /** 关联核保单ID（承保依据溯源） */
    @Column(name = "underwriting_id", length = 36)
    private String        underwritingId;

    /** 营销包ID（弱引用 marketing 域，用于渠道转化率统计） */
    @Column(name = "market_package_id", length = 36)
    private String        marketPackageId;

    /** 主险产品ID（险种段真相在 t_policy_product，此列为高频查询冗余） */
    @Column(name = "product_id", length = 36)
    private String        productId;

    /** 主险保额（各段保额见 t_policy_product） */
    @Column(name = "sum_insured", precision = 18, scale = 2)
    private BigDecimal    sumInsured;

    /** 保单总保费（= Σ 计入段的保费，拒保段已剔除） */
    @Column(name = "total_premium", precision = 18, scale = 2)
    private BigDecimal    totalPremium;

    /** 险种段数量（单险种保单为 1，一单多险 > 1） */
    @Column(name = "line_count")
    private Integer       lineCount;

    /** 等待期届满日（此前疾病类责任不赔） */
    @Column(name = "waiting_period_end_date")
    private LocalDateTime waitingPeriodEndDate;

    /** 犹豫期届满日（此前可无条件退保） */
    @Column(name = "hesitation_period_end_date")
    private LocalDateTime hesitationPeriodEndDate;

    /** 收费方式码（OFFLINE/ONLINE/FREE/PAY_AFTER_USE/WITHHOLD） */
    @Column(name = "collection_mode", length = 32)
    private String        collectionMode;

    /** 收讫状态码（UNCOLLECTED/PARTIALLY_COLLECTED/COLLECTED/DEFERRED/OVERDUE） */
    @Column(name = "collection_status", length = 32)
    private String        collectionStatus;

    /** 已收保费金额 */
    @Column(name = "collected_amount", precision = 18, scale = 2)
    private BigDecimal    collectedAmount;

    /** 渠道ID（指向 channel 域） */
    @Column(name = "channel_id", length = 36)
    private String        channelId;

    /** 销售渠道大类码（AGENT/BANCASSURANCE/ONLINE/BROKER/...） */
    @Column(name = "sales_channel", length = 32)
    private String        salesChannel;

    /** 代理人/业务员ID */
    @Column(name = "agent_id", length = 36)
    private String        agentId;
}
