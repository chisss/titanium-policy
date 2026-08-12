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
 * 保单条款快照读模型实体（L2.5，CQRS 读侧）
 * <p>
 * 记录本保单各险种段适用的条款及其<b>版本</b>。条款域后续修订产生新版本聚合，
 * 已签发保单仍适用此处冻结的版本——符合「保险合同以订立时条款为准」。
 * </p>
 * <p>
 * 由 {@code PolicyLineProjectionEventHandler} 从险种段内 {@code clauseSnapshots} 拆解投影，
 * 映射 {@code t_policy_clause}。查看条款全文或免责条款时经 {@link #clauseId} 穿透至 clause 域。
 * </p>
 */
@Entity
@Table(name = "t_policy_clause", indexes = {
        @Index(name = "idx_policy_clause_policy", columnList = "policy_id, tenant_id"),
        @Index(name = "idx_policy_clause_line", columnList = "policy_product_id"),
        @Index(name = "idx_policy_clause_clause", columnList = "clause_id"),
        @Index(name = "idx_policy_clause_tenant", columnList = "tenant_id")
})
@Getter
@Setter
public class PolicyClauseView extends BaseView {

    /** 主键（policyId + 段ID + 条款ID 派生，保证投影幂等） */
    @Id
    @Column(name = "id", nullable = false, length = 128)
    private String  id;

    /** 保单ID */
    @Column(name = "policy_id", nullable = false, length = 36)
    private String  policyId;

    /** 所属险种段ID */
    @Column(name = "policy_product_id", nullable = false, length = 64)
    private String  policyProductId;

    /** 条款ID（指向 clause 域） */
    @Column(name = "clause_id", nullable = false, length = 36)
    private String  clauseId;

    /** 条款编码（快照） */
    @Column(name = "clause_code", length = 64)
    private String  clauseCode;

    /** 条款名称（快照） */
    @Column(name = "clause_name", length = 256)
    private String  clauseName;

    /** 条款版本（快照，签发即冻结） */
    @Column(name = "clause_version", length = 32)
    private String  clauseVersion;

    /** 是否主条款（一段仅一个主条款，其余为附加条款） */
    @Column(name = "is_main_clause", nullable = false)
    private Boolean mainClause;
}
