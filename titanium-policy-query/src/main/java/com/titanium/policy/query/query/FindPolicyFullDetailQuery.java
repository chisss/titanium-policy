package com.titanium.policy.query.query;

/**
 * 保单全景查询（一次取全九个维度）
 * <p>
 * 后台按保单ID查看完整保单构成：产品/条款/责任/标的/参与方/缴费/收费/期间/渠道。
 * </p>
 *
 * @param policyId 保单ID
 * @param tenantId 租户ID
 */
public record FindPolicyFullDetailQuery(String policyId, String tenantId) {
}
