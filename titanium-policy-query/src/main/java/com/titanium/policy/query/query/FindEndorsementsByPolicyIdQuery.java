package com.titanium.policy.query.query;

/**
 * 按保单ID查询批改历史列表
 *
 * @param policyId 保单ID
 * @param tenantId 租户ID
 */
public record FindEndorsementsByPolicyIdQuery(String policyId, String tenantId) {
}
