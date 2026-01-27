package com.titanium.policy.valueobject;

/**
 * 免赔规则值对象
 * <p>
 * 定义理赔时的免赔规则，包括免赔类型、免赔值、适用责任范围和免赔触发条件
 * </p>
 *
 * @param deductibleType 免赔类型：金额/比例
 * @param deductibleValue 免赔值
 * @param applicableCoverage 适用责任范围
 * @param deductibleCondition 免赔触发条件
 */
public record DeductibleRule(DeductibleType deductibleType, double deductibleValue, String applicableCoverage,
                             String deductibleCondition) {
    /**
     * 免赔类型枚举
     */
    public enum DeductibleType {
        /**
         * 金额免赔
         */
        AMOUNT("AMOUNT", "金额免赔"),
        /**
         * 比例免赔
         */
        PERCENTAGE("PERCENTAGE", "比例免赔");

        private final String code;
        private final String name;

        DeductibleType(String code, String name) {
            this.code = code;
            this.name = name;
        }
    }
}
