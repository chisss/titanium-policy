package com.titanium.policy.query.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.metadata.enums.policy.PolicyEnum;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * 保单读模型实体（CQRS Projection）
 * <p>
 * 对应读模型表 {@code t_policy_view}，与写侧聚合根持久化表 {@code t_policy} 物理隔离。 由
 * {@link com.titanium.policy.query.handler.PolicyProjectionEventHandler} 订阅领域事件投影而来，
 * 仅供查询侧使用，禁止写侧逻辑直接操作。
 * </p>
 * <p>
 * <b>字段填充说明</b>：字段值来源于领域事件。部分业务字段（如投保人姓名、险种名称）当前领域事件 未携带，投影时留空，后续可通过事件增强或跨域事件补全。
 * </p>
 */
@Entity
@Table(name = "t_policy_view")
@Getter
@Setter
public class PolicyView {

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

    /** 投保人ID（事件暂未携带，预留） */
    @Column(name = "policy_holder_id", length = 36)
    private String        policyHolderId;

    /** 投保人姓名（事件暂未携带，预留） */
    @Column(name = "policy_holder_name", length = 128)
    private String        policyHolderName;

    /** 被保险人姓名（事件暂未携带，预留） */
    @Column(name = "insured_name", length = 128)
    private String        insuredName;

    /** 险种编码（事件暂未携带，预留） */
    @Column(name = "product_code", length = 64)
    private String        productCode;

    /** 租户ID（多租户隔离） */
    @Column(name = "tenant_id", nullable = false, length = 36)
    private String        tenantId;

    /** 读模型创建时间（投影写入时间） */
    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    /** 读模型更新时间（最近一次投影时间） */
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    /** 保单当前业务版本号（批改后递增；区别于下方乐观锁 version） */
    @Column(name = "current_version")
    private Integer       currentVersion;

    /** 乐观锁版本，防止并发投影覆盖 */
    @Version
    @Column(name = "version")
    private Long          version;
}
