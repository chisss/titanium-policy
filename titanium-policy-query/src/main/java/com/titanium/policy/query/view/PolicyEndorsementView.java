package com.titanium.policy.query.view;

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
 * 批单读模型实体（保单批改流水）
 * <p>
 * 由 PolicyEndorsedEvent 投影维护，记录保单每次数据/要素类批改的批单凭证。
 * 以批单号为主键，支撑按保单查批改流水。
 * </p>
 * <p>
 * 继承 {@link BaseView}，复用租户ID、创建/更新时间（投影时间）、乐观锁版本等读模型公共字段。
 * </p>
 */
@Entity
@Table(name = "t_policy_endorsement_view", indexes = {
        @Index(name = "idx_endorsement_policy", columnList = "policy_id, tenant_id")
})
@Getter
@Setter
public class PolicyEndorsementView extends BaseView {

    /** 批单号（读模型主键） */
    @Id
    @Column(name = "endorsement_no", nullable = false, length = 64)
    private String        endorsementNo;

    /** 保单ID */
    @Column(name = "policy_id", nullable = false, length = 36)
    private String        policyId;

    /** 批改类型编码 */
    @Column(name = "update_type", nullable = false, length = 40)
    private String        updateType;

    /** 批改大类编码 */
    @Column(name = "category", nullable = false, length = 20)
    private String        category;

    /** 批改后保单版本号 */
    @Column(name = "policy_version", nullable = false)
    private int           policyVersion;

    /** 批单生效日 */
    @Column(name = "effective_date")
    private LocalDateTime effectiveDate;

    /** 变更摘要 */
    @Column(name = "change_summary", length = 512)
    private String        changeSummary;

    /** 是否触发保费重算 */
    @Column(name = "requires_premium_recalc", nullable = false)
    private boolean       requiresPremiumRecalc;

    /** 来源保全案件ID */
    @Column(name = "source_maintenance_id", length = 36)
    private String        sourceMaintenanceId;

    /** 操作人 */
    @Column(name = "operator_id", length = 50)
    private String        operatorId;

    /** 批改落地时间 */
    @Column(name = "endorsed_at", nullable = false)
    private LocalDateTime endorsedAt;
}
