package com.titanium.policy.query.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 保单父子关系读模型实体
 * <p>
 * 由 {@code SubPolicyLinkedEvent} 投影维护，记录团单/父子保单的父→子映射，
 * 支撑父保单状态变更时的子保单级联编排（跨聚合，无法在聚合内完成）。
 * </p>
 */
@Entity
@Table(name = "t_policy_relation", indexes = {
        @Index(name = "idx_policy_relation_parent", columnList = "parent_policy_id, tenant_id")
})
@Getter
@Setter
public class PolicyRelationView {

    /** 子保单ID（一个子保单唯一归属一个父保单，作主键） */
    @Id
    @Column(name = "child_policy_id", nullable = false, length = 36)
    private String        childPolicyId;

    /** 父保单ID */
    @Column(name = "parent_policy_id", nullable = false, length = 36)
    private String        parentPolicyId;

    /** 集团ID（团单专属） */
    @Column(name = "group_id", length = 36)
    private String        groupId;

    /** 租户ID（多租户隔离） */
    @Column(name = "tenant_id", nullable = false, length = 36)
    private String        tenantId;

    /** 挂载时间 */
    @Column(name = "linked_at", nullable = false)
    private LocalDateTime linkedAt;
}
