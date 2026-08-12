package com.titanium.policy.query.result;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 保单标的查询结果（L3，读侧对外契约）
 * <p>
 * 标的是全险种差异的收敛点：{@link #attributesJson} 承载各险种特有属性（车险的 VIN/初登日期/
 * NCD 系数、企财险的建筑结构/消防等级、寿险的职业类别/吸烟状况），结构由产品
 * {@code subjectFieldsSchema} 定义。调用方按 {@link #subjectType} 解析。
 * </p>
 */
@Data
public class PolicySubjectQueryResult {

    /** 保单ID */
    private String     policyId;

    /** 所属险种段ID */
    private String     policyProductId;

    /** 标的ID */
    private String     subjectId;

    /** 标的名称（车牌号 / 被保险人姓名 / 厂房名称） */
    private String     subjectName;

    /** 标的类型码（PERSON/VEHICLE/PROPERTY/CARGO 等） */
    private String     subjectType;

    /** 客户主数据ID（人身类标的引用 customer 域） */
    private String     customerId;

    /** 本标的保额（多车/多分项时各不同） */
    private BigDecimal subjectSumInsured;

    /** 标的风险等级码（核保回写） */
    private String     riskLevel;

    /** 类型化属性包 JSON（结构由产品 Schema 定义） */
    private String     attributesJson;
}
