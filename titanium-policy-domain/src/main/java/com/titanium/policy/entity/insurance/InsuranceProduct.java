package com.titanium.policy.entity.insurance;

import com.titanium.policy.valueobject.Amount;

/**
 * 投保险种实体
 * <p>
 * 记录投保险种的详细信息，包括险种编码、险种名称、保额、保费系数等
 * </p>
 */
public record InsuranceProduct(
        /**
         * 险种ID，聚合内唯一
         */
        String lineId,
        /**
         * 险种编码
         */
        String productCode,
        /**
         * 险种名称
         */
        String productName,
        /**
         * 保额
         */
        Amount sumInsured,
        /**
         * 保费系数
         */
        double premiumFactor,
        /**
         * 是否为主险
         */
        boolean isMainLine
) {
    /**
     * 新增投保险种
     * <p>
     * 创建新的投保险种实体
     * </p>
     *
     * @param lineId        险种ID
     * @param productCode   险种编码
     * @param productName   险种名称
     * @param sumInsured    保额
     * @param premiumFactor 保费系数
     * @param isMainLine    是否为主险
     * @return 投保险种实体
     */
    public static InsuranceProduct addInsuranceLine(String lineId, String productCode, String productName,
                                                    Amount sumInsured, double premiumFactor, boolean isMainLine) {
        return new InsuranceProduct(lineId, productCode, productName, sumInsured, premiumFactor, isMainLine);
    }

    /**
     * 校验险种约束
     * <p>
     * 调用产品域校验险种约束（如年龄、保额上限）
     * </p>
     *
     * @return 校验结果，true表示校验通过
     */
    public boolean verifyLineConstraint() {
        // 这里应该调用产品域的险种约束校验规则
        // 暂时返回true
        return true;
    }
}