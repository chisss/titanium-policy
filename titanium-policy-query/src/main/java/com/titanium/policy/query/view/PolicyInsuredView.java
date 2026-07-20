package com.titanium.policy.query.view;

import com.titanium.common.jpa.BaseView;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 保单被保险人读模型实体（CQRS 读侧）
 * <p>
 * 由 {@code PolicyCreatedEvent} 拆解 insuredPartyList 投影而来，映射 {@code t_policy_insured}，
 * 每行对应一名被保险人快照。与保单主视图 {@link PolicyView} 1:N 关联。
 * </p>
 */
@Entity
@Table(name = "t_policy_insured", indexes = {
        @Index(name = "idx_policy_insured_policy", columnList = "policy_id, tenant_id"),
        @Index(name = "idx_policy_insured_tenant", columnList = "tenant_id")
})
@Getter
@Setter
public class PolicyInsuredView extends BaseView {

    /** 主键（UUID 去连字符，32 位，出单时按 policyId+顺序生成确定性 ID） */
    @Id
    @Column(name = "id", nullable = false, length = 32)
    private String id;

    /** 保单ID */
    @Column(name = "policy_id", nullable = false, length = 36)
    private String policyId;

    /** 被保险人客户ID（引用 customer 域主数据） */
    @Column(name = "customer_id", nullable = false, length = 36)
    private String customerId;

    /** 被保险人姓名（出单快照） */
    @Column(name = "insured_name", length = 128)
    private String insuredName;

    /** 与投保人关系（投保关系角色码，预留字段） */
    @Column(name = "relation", length = 32)
    private String relation;

    /** 家庭成员关系码（家庭险专属，对应 FamilyRelation 枚举 code） */
    @Column(name = "family_relation", length = 32)
    private String familyRelation;
}
