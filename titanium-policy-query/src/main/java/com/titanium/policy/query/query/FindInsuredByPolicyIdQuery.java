package com.titanium.policy.query.query;

/**
 * 按保单ID查询被保险人清单
 *
 * @param policyId 保单ID
 * @param tenantId 租户ID
 */
public record FindInsuredByPolicyIdQuery(String policyId, String tenantId) {
}
