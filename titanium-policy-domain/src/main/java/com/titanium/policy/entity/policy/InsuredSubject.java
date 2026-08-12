package com.titanium.policy.entity.policy;

import java.math.BigDecimal;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.titanium.metadata.enums.insurance.SubjectType;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.RiskLevel;
import com.titanium.metadata.valueobject.Money;

/**
 * 保险标的实体（L3）
 * <p>
 * 标的（Subject / Coverable）是保险合同保障的具体对象，也是<b>全险种差异的唯一收敛点</b>：
 * 寿险标的是被保险人的生命、车险是机动车辆、企财险是厂房设备、货运险是货物批次。
 * 所有险种差异最终都归结为「标的是什么 + 标的有哪些属性 + 责任怎么挂到标的上」。
 * </p>
 * <p>
 * 🔴 <b>Schema 驱动的类型化属性包</b>：不同险种的标的属性差异极大（车辆 20 余字段、厂房 15 余字段），
 * 不可能穷举为固定字段。{@link #attributes} 以键值对承载，其字段结构与校验规则由 product 域
 * {@code PolicyStructureConfig.subjectFieldsSchema}（JSON Schema）定义。<b>新增险种只需配 Schema，
 * policy 域代码不改</b>——这是骨架支撑全险种的关键机制。
 * </p>
 * <p>
 * 取代原 {@code entity/Subject}（其 {@code detailInfo} 为裸 String，装不下车辆的多字段结构）。
 * 人身类标的以 {@link #customerId} 引用 customer 域主数据；非人身类标的该字段为空。
 * </p>
 *
 * @param subjectId         标的ID（保单内唯一）
 * @param subjectName       标的名称（车牌号 / 被保险人姓名 / 厂房名称等，展示用）
 * @param subjectType       标的类型
 * @param customerId        客户主数据ID（人身类标的引用 customer 域；非人身类为 null）
 * @param subjectSumInsured 本标的保额（多车投保 / 企财多分项时各标的保额不同）
 * @param riskLevel         标的风险等级（核保回写；未核保为 null）
 * @param attributes        类型化属性包（结构由产品 subjectFieldsSchema 定义）
 */
public record InsuredSubject(String subjectId, String subjectName, SubjectType subjectType, String customerId,
                             Money subjectSumInsured, RiskLevel riskLevel, Map<String, Object> attributes) {

    /**
     * 构建人身类标的（引用 customer 域客户主数据）。
     *
     * @param subjectId  标的ID
     * @param customerId 客户主数据ID
     * @param name       被保险人姓名
     * @param sumInsured 标的保额
     * @param attributes 属性包（年龄/性别/职业类别等）
     * @return 人身类标的
     */
    public static InsuredSubject ofPerson(String subjectId, String customerId, String name, Money sumInsured,
                                          Map<String, Object> attributes) {
        return new InsuredSubject(subjectId, name, SubjectType.PERSON, customerId, sumInsured, null,
                attributes != null ? Map.copyOf(attributes) : Map.of());
    }

    /**
     * 构建非人身类标的（车辆/财产/货物等，无客户主数据引用）。
     *
     * @param subjectId  标的ID
     * @param type       标的类型
     * @param name       标的名称
     * @param sumInsured 标的保额
     * @param attributes 属性包（由产品 Schema 定义）
     * @return 非人身类标的
     */
    public static InsuredSubject ofObject(String subjectId, SubjectType type, String name, Money sumInsured,
                                          Map<String, Object> attributes) {
        return new InsuredSubject(subjectId, name, type, null, sumInsured, null,
                attributes != null ? Map.copyOf(attributes) : Map.of());
    }

    /**
     * 是否为人身类标的（以被保险人生命或身体为标的）。
     *
     * @return 人身类返回 {@code true}
     */
    @JsonIgnore
    public boolean isPerson() {
        return subjectType != null && subjectType.isPersonal();
    }

    /**
     * 回写核保裁定的标的风险等级。
     *
     * @param level 风险等级
     * @return 回写后的新实例
     */
    public InsuredSubject withRiskLevel(RiskLevel level) {
        return new InsuredSubject(subjectId, subjectName, subjectType, customerId, subjectSumInsured, level,
                attributes);
    }

    /**
     * 读取字符串型标的属性。
     *
     * @param key 属性键
     * @return 属性值；不存在返回 null
     */
    public String attributeAsString(String key) {
        Object value = attributeOf(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 读取整型标的属性（如车辆使用年限、被保险人年龄）。
     *
     * @param key 属性键
     * @return 属性值；不存在或非数值返回 null
     */
    public Integer attributeAsInt(String key) {
        Object value = attributeOf(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 读取数值型标的属性（如新车购置价、NCD 系数）。
     *
     * @param key 属性键
     * @return 属性值；不存在或非数值返回 null
     */
    public BigDecimal attributeAsDecimal(String key) {
        Object value = attributeOf(key);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 属性包是否包含指定键（供 Schema 必填项校验使用）。
     *
     * @param key 属性键
     * @return 存在且值非空返回 {@code true}
     */
    public boolean hasAttribute(String key) {
        return attributeOf(key) != null;
    }

    /**
     * 空安全读取属性原始值。
     */
    private Object attributeOf(String key) {
        return attributes != null ? attributes.get(key) : null;
    }
}
