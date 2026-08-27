package com.titanium.policy.query.view;

import java.math.BigDecimal;

import com.titanium.common.jpa.BaseView;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 保单受益人读模型实体（CQRS 读侧）
 * <p>
 * 由 {@code PolicyCreatedEvent} 拆解 insuredPartyList.beneficiaryList 投影而来，
 * 映射 {@code t_policy_beneficiary}，每行对应一名受益人快照（含受益类型/顺位/份额）。
 * </p>
 */
@Entity
@Table(name = "t_policy_beneficiary", indexes = {
        @Index(name = "idx_policy_beneficiary_policy", columnList = "policy_id, tenant_id"),
        @Index(name = "idx_policy_beneficiary_tenant", columnList = "tenant_id")
})
@Getter
@Setter
public class PolicyBeneficiaryView extends BaseView {

    /** 主键（UUID 去连字符，32 位，出单时按 policyId+顺序生成确定性 ID） */
    @Id
    @Column(name = "id", nullable = false, length = 32)
    private String id;

    /** 保单ID */
    @Column(name = "policy_id", nullable = false, length = 36)
    private String policyId;

    /** 受益人客户ID（引用 customer 域主数据） */
    /**
     * 受益人客户ID
     * <p>
     * 🔴 可空：受益人未必是本平台注册客户——投保时常只提供「姓名 + 证件类型 + 证件号」而不建客户档案
     * （如指定未成年子女、父母为身故受益人）。故此列不设非空约束，身份以 name/certNo 承载。
     * </p>
     */
    @Column(name = "customer_id", length = 36)
    private String customerId;

    /** 受益人姓名（出单快照） */
    @Column(name = "beneficiary_name", length = 128)
    private String beneficiaryName;

    /** 受益人证件类型快照 */
    @Column(name = "id_type", length = 32)
    private String idType;

    /** 受益人证件号码快照 */
    @Column(name = "id_no", length = 64)
    private String idNo;

    /** 受益人性别快照 */
    @Column(name = "gender", length = 16)
    private String gender;

    /** 受益人手机号快照 */
    @Column(name = "phone", length = 32)
    private String phone;

    /** 受益类型码（DEATH 身故受益人 / SURVIVAL 生存受益人） */
    @Column(name = "beneficiary_type", length = 32)
    private String beneficiaryType;

    /** 受益顺位（1=第一顺位） */
    @Column(name = "order_no", nullable = false)
    private Integer orderNo;

    /** 受益份额百分比（同一顺位内份额，100=100%；原始 ratio×100 转存） */
    @Column(name = "share_ratio", nullable = false, precision = 5, scale = 2)
    private BigDecimal shareRatio;
}
