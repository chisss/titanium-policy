package com.titanium.policy.query.view;

import java.math.BigDecimal;

import com.titanium.common.jpa.BaseView;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 保单标的读模型实体（L3，CQRS 读侧）
 * <p>
 * 标的是<b>全险种差异的唯一收敛点</b>：寿险标的是被保险人生命、车险是机动车辆、企财险是厂房设备、
 * 货运险是货物批次。各险种的标的属性差异极大（车辆 20 余字段、厂房 15 余字段），不可能穷举为固定列，
 * 故以 {@link #attributesJson} 承载，其结构由 product 域 {@code PolicyStructureConfig.subjectFieldsSchema}
 * （JSON Schema）定义。<b>新增险种只需配 Schema，读侧表结构不变</b>。
 * </p>
 * <p>
 * 由 {@code PolicySubjectProjectionEventHandler} 从险种段内 {@code insuredSubjects} 拆解投影，
 * 映射 {@code t_policy_subject}。人身类标的以 {@link #customerId} 引用 customer 域主数据。
 * </p>
 */
@Entity
@Table(name = "t_policy_subject", indexes = {
        @Index(name = "idx_policy_subject_policy", columnList = "policy_id, tenant_id"),
        @Index(name = "idx_policy_subject_line", columnList = "policy_product_id"),
        @Index(name = "idx_policy_subject_customer", columnList = "customer_id, tenant_id"),
        @Index(name = "idx_policy_subject_tenant", columnList = "tenant_id")
})
@Getter
@Setter
public class PolicySubjectView extends BaseView {

    /** 主键（policyId + 段ID + 标的ID 派生，保证投影幂等） */
    @Id
    @Column(name = "id", nullable = false, length = 128)
    private String     id;

    /** 保单ID */
    @Column(name = "policy_id", nullable = false, length = 36)
    private String     policyId;

    /** 所属险种段ID */
    @Column(name = "policy_product_id", nullable = false, length = 64)
    private String     policyProductId;

    /** 标的ID（保单内唯一） */
    @Column(name = "subject_id", nullable = false, length = 64)
    private String     subjectId;

    /** 标的名称（车牌号 / 被保险人姓名 / 厂房名称） */
    @Column(name = "subject_name", length = 256)
    private String     subjectName;

    /** 标的类型码（PERSON/VEHICLE/PROPERTY/CARGO/VESSEL/AIRCRAFT/AGRICULTURAL 等） */
    @Column(name = "subject_type", nullable = false, length = 32)
    private String     subjectType;

    /** 客户主数据ID（人身类标的引用 customer 域；非人身类为空） */
    @Column(name = "customer_id", length = 36)
    private String     customerId;

    /** 本标的保额（多车投保 / 企财多分项时各标的保额不同） */
    @Column(name = "subject_sum_insured", precision = 18, scale = 2)
    private BigDecimal subjectSumInsured;

    /** 标的风险等级码（核保回写） */
    @Column(name = "risk_level", length = 32)
    private String     riskLevel;

    /**
     * 类型化属性包（JSON）
     * <p>
     * 车险存 VIN/车牌/初登日期/NCD 系数；企财险存坐落地址/建筑结构/消防等级；
     * 寿险存年龄/性别/职业类别/吸烟状况。结构由产品 subjectFieldsSchema 定义与校验。
     * </p>
     */
    @Lob
    @Column(name = "attributes_json", columnDefinition = "TEXT")
    private String     attributesJson;
}
