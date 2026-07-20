package com.titanium.policy.query.query;

/**
 * 按保单ID查询受益人清单
 *
 * @param policyId 保单ID
 * @param tenantId 租户ID
 */
public record FindBeneficiariesByPolicyIdQuery(String policyId, String tenantId) {
}
