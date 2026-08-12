package com.titanium.policy.valueobject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.titanium.metadata.enums.insurance.SubjectType;
import com.titanium.metadata.enums.product.ProductEnum.PaymentFrequency;
import com.titanium.metadata.enums.product.ProductEnum.PeriodUnit;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.metadata.valueobject.Money;

/**
 * 出单方案行值对象（一个险种段的投保意图）
 * <p>
 * 出单请求以<b>方案行列表</b>而非扁平字段承载多险种：一单多险时保额、保障期间、缴费条件、标的
 * 全部是「按险种段」而非「按保单」的量。扁平字段无法表达「主险 20 年缴 50 万 + 附加重疾 1 年期 30 万」。
 * </p>
 * <p>
 * 本值对象是<b>投保意图</b>（调用方声明的期望），经出单编排器校验、试算、装配条款责任快照后，
 * 才成为投保段 {@code InsuranceLine} 或保单段 {@code PolicyProduct}。三者不可混用。
 * </p>
 *
 * @param lineNo              段序号（保单内唯一，RIDER 以此关联主险）
 * @param productId           产品ID
 * @param productCategory     产品类别（MAIN 主险 / RIDER 附加险）
 * @param parentLineNo        依附的主险段序号（RIDER 必填，MAIN 为 null）
 * @param sumInsured          本险种投保保额
 * @param coveragePeriodValue 保障期限数值（如 20 表示 20 年；与起止期二选一）
 * @param coveragePeriodUnit  保障期限单位
 * @param paymentFrequency    缴费频率（趸/年/半年/季/月）
 * @param premiumPaymentYears 缴费年数（缴费期 ≠ 保障期）
 * @param subjects            本险种承保标的（人身类传 customerId，物类传 attributes）
 * @param extendData          险种段特有要素（健康告知答案、验车结论等，本期透传存档）
 */
public record IssuancePlanLine(int lineNo, String productId, ProductCategory productCategory, Integer parentLineNo,
                               Money sumInsured, Integer coveragePeriodValue, PeriodUnit coveragePeriodUnit,
                               PaymentFrequency paymentFrequency, Integer premiumPaymentYears,
                               List<SubjectIntent> subjects, Map<String, Object> extendData) {

    /**
     * 是否为主险段。
     *
     * @return 主险返回 {@code true}
     */
    @JsonIgnore
    public boolean isMain() {
        return productCategory == ProductCategory.MAIN;
    }

    /**
     * 是否为附加险段。
     *
     * @return 附加险返回 {@code true}
     */
    @JsonIgnore
    public boolean isRider() {
        return productCategory == ProductCategory.RIDER;
    }

    /**
     * 取本段各标的的指定属性值（字符串形式，空安全）。
     * <p>
     * 标的属性取值是「只依赖自身字段」的派生计算，按充血原则内聚于此——投保要素校验、核保取数、
     * 保费试算三处都要读标的属性，逻辑放在调用方会重复三遍。
     * </p>
     *
     * @param key 属性键（如 occupation / region / age）
     * @return 属性值列表（不含空值）；无标的或无该属性时返回空列表
     */
    public List<String> subjectAttributeTexts(String key) {
        if (subjects == null || subjects.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (SubjectIntent subject : subjects) {
            String value = subject.attributeText(key);
            if (value != null) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    /**
     * 取本段各标的的指定整型属性值（非数值项跳过）。
     *
     * @param key 属性键（如 age）
     * @return 整型属性值列表；无有效值时返回空列表
     */
    public List<Integer> subjectAttributeInts(String key) {
        List<Integer> values = new ArrayList<>();
        for (String text : subjectAttributeTexts(key)) {
            Integer parsed = parseInt(text);
            if (parsed != null) {
                values.add(parsed);
            }
        }
        return List.copyOf(values);
    }

    /**
     * 字符串转整数（非数值返回 null）。
     */
    private static Integer parseInt(String text) {
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 首要被保险人的客户ID（首个人身类标的），供保费试算与核保取要素。
     *
     * @return 客户ID；无人身类标的时返回 null
     */
    public String primaryInsuredCustomerId() {
        if (subjects == null) {
            return null;
        }
        return subjects.stream()
                .filter(subject -> subject.subjectType() != null && subject.subjectType().isPersonal())
                .map(SubjectIntent::customerId)
                .filter(id -> id != null)
                .findFirst()
                .orElse(null);
    }

    /**
     * 标的投保意图值对象
     * <p>
     * 人身类标的（寿险/医疗/意外）以 {@code customerId} 引用 customer 域客户主数据；
     * 物类标的（车辆/财产/货物）以 {@code attributes} 承载属性，其字段结构由产品
     * {@code PolicyStructureConfig.subjectFieldsSchema} 校验。
     * </p>
     *
     * @param subjectType       标的类型
     * @param customerId        客户主数据ID（人身类必填）
     * @param subjectName       标的名称（车牌号 / 厂房名称；人身类可空，取客户主数据姓名）
     * @param subjectSumInsured 本标的保额（多车/多分项时各不同；单标的可空，取段保额）
     * @param relationToHolder  与投保人关系（可保利益校验用）
     * @param attributes        标的属性包（物类标的必填，结构由产品 Schema 定义）
     */
    public record SubjectIntent(SubjectType subjectType, String customerId, String subjectName,
                                Money subjectSumInsured, String relationToHolder, Map<String, Object> attributes) {

        /**
         * 是否为人身类标的。
         *
         * @return 人身类返回 {@code true}
         */
        @JsonIgnore
        public boolean isPerson() {
            return subjectType != null && subjectType.isPersonal();
        }

        /**
         * 空安全读取属性值（字符串形式）。
         *
         * @param key 属性键
         * @return 属性值；属性包为空或无该键时返回 null
         */
        public String attributeText(String key) {
            Object value = attributes != null ? attributes.get(key) : null;
            return value != null ? value.toString() : null;
        }
    }
}
