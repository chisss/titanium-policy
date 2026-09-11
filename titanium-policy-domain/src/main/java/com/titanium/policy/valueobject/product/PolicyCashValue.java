package com.titanium.policy.valueobject.product;

import java.math.BigDecimal;

/**
 * 保单现金价值（产品域防腐值对象）。
 * <p>
 * 现金价值的计算规则属产品域（退保价值策略按产品 + 保单年度配置现金价值率），保单域不自行推算，
 * 本值对象仅承载产品域返回的结果，隔离对端契约细节。
 * </p>
 * <p>
 * 承载策略取证信息（编码/版本/内容哈希），使算出的金额可追溯到具体策略版本——退保、垫缴等
 * 涉金场景需要事后复核「当时用的是哪一版规则」。
 * </p>
 *
 * @param productId 产品ID
 * @param policyYear 保单年度（自 1 起）
 * @param withinCoolingOff 估值日是否落在犹豫期内（犹豫期内全额退还）
 * @param refundType 退保类型
 * @param cashValueRate 适用的现金价值率
 * @param cashValue 现金价值金额
 * @param policyCode 退保价值策略编码
 * @param policyVersion 退保价值策略版本
 * @param policyContentHash 退保价值策略内容哈希
 */
public record PolicyCashValue(
        String productId,
        int policyYear,
        boolean withinCoolingOff,
        String refundType,
        BigDecimal cashValueRate,
        BigDecimal cashValue,
        String policyCode,
        String policyVersion,
        String policyContentHash) {
}
