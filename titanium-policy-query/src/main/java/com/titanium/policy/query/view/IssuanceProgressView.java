package com.titanium.policy.query.view;

import java.math.BigDecimal;

import com.titanium.common.jpa.BaseView;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 出单进度读模型实体（CQRS 读侧）
 * <p>
 * 承载两个职责：
 * </p>
 * <ol>
 *   <li><b>幂等依据</b>：{@code (tenant_id, biz_no)} 唯一约束——同一业务流水号重复提交时，
 *       编排器查到已有记录即返回首次结果，不产生第二张单据；</li>
 *   <li><b>进度查询</b>：两步/三步出单是异步长流程（含核保与收费），调用方经
 *       {@code GET /api/v1/issuances/{bizNo}} 轮询当前阶段与各单据ID。</li>
 * </ol>
 * <p>
 * 与其它读模型不同，本表在编排开始前由应用层建立幂等基线；没有聚合事件的同步拒绝也随基线一次性
 * 写入。首个编排命令发出后，阶段与单据关联只由领域事件投影更新，避免入口线程与 tracking processor
 * 并发修改同一乐观锁行。
 * </p>
 */
@Entity
@Table(name = "t_issuance_progress",
        uniqueConstraints = @UniqueConstraint(name = "uk_issuance_biz", columnNames = { "tenant_id", "biz_no" }),
        indexes = {
                @Index(name = "idx_issuance_progress_policy", columnList = "policy_id"),
                @Index(name = "idx_issuance_progress_insurance", columnList = "insurance_id"),
                @Index(name = "idx_issuance_progress_tenant", columnList = "tenant_id")
        })
@Getter
@Setter
public class IssuanceProgressView extends BaseView {

    /** 主键（tenantId + bizNo 派生，保证幂等） */
    @Id
    @Column(name = "id", nullable = false, length = 96)
    private String     id;

    /** 业务流水号（调用方提供，同租户内唯一） */
    @Column(name = "biz_no", nullable = false, length = 64)
    private String     bizNo;

    /** 营销包ID（弱引用） */
    @Column(name = "market_package_id", length = 36)
    private String     marketPackageId;

    /** 出单策略码（MERGE_ONE_POLICY/SPLIT_MULTI_POLICY） */
    @Column(name = "issuance_strategy", length = 32)
    private String     issuanceStrategy;

    /** 出单模式码（ONE_STEP/TWO_STEP/THREE_STEP，由产品配置决定） */
    @Column(name = "issuance_mode", length = 32)
    private String     issuanceMode;

    /** 当前阶段码 */
    @Column(name = "current_stage", nullable = false, length = 32)
    private String     currentStage;

    /** 主险产品ID */
    @Column(name = "product_id", length = 36)
    private String     productId;

    /** 投保人客户ID */
    @Column(name = "holder_customer_id", length = 36)
    private String     holderCustomerId;

    /** 意向单ID（三步出单） */
    @Column(name = "proposal_id", length = 36)
    private String     proposalId;

    /** 投保单ID（两步/三步出单） */
    @Column(name = "insurance_id", length = 36)
    private String     insuranceId;

    /** 保单ID（合并出单策略下的唯一保单） */
    @Column(name = "policy_id", length = 36)
    private String     policyId;

    /** 保单ID列表 JSON（拆分出单策略下的多张保单，合并策略为空） */
    @Column(name = "policy_ids", length = 1024)
    private String     policyIds;

    /** 核保单ID */
    @Column(name = "underwriting_id", length = 36)
    private String     underwritingId;

    /** 账单ID（billing 域） */
    @Column(name = "bill_id", length = 64)
    private String     billId;

    /** 支付单ID（payment 域） */
    @Column(name = "payment_order_id", length = 64)
    private String     paymentOrderId;

    /** 系统试算的标准保费 */
    @Column(name = "standard_premium", precision = 18, scale = 2)
    private BigDecimal standardPremium;

    /** 应付保费（标准保费 + 核保加费） */
    @Column(name = "payable_premium", precision = 18, scale = 2)
    private BigDecimal payablePremium;

    /** 险种段数量 */
    @Column(name = "line_count")
    private Integer    lineCount;

    /** 拒绝业务码（受理失败时） */
    @Column(name = "reject_code", length = 64)
    private String     rejectCode;

    /** 拒绝原因（含违反的具体规则与所在险种段） */
    @Column(name = "reject_reason", length = 512)
    private String     rejectReason;
}
