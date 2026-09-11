package com.titanium.policy.api.response;

import java.math.BigDecimal;

/**
 * 保单现金价值查询结果（保单域对外契约）。
 * <p>
 * 现金价值由产品域按退保价值策略算出，保单域以其保单要素（产品、总保费、生效日）代为问询，
 * 使调用方只需保单号即可取值。
 * </p>
 *
 * @param policyId 保单ID
 * @param productId 产品ID
 * @param policyYear 保单年度（自 1 起）
 * @param withinCoolingOff 估值日是否落在犹豫期内（命中则全额退还）
 * @param refundType 退保类型
 * @param cashValueRate 适用的现金价值率
 * @param cashValue 现金价值金额
 * @param policyCode 退保价值策略编码（取证用）
 * @param policyVersion 退保价值策略版本（取证用）
 * @param policyContentHash 退保价值策略内容哈希（取证用）
 */
public record PolicyCashValueResponse(
        String policyId,
        String productId,
        Integer policyYear,
        boolean withinCoolingOff,
        String refundType,
        BigDecimal cashValueRate,
        BigDecimal cashValue,
        String policyCode,
        String policyVersion,
        String policyContentHash) {
}
