package com.titanium.policy.query.query;

/**
 * 根据ID查询保单
 */
public record FindPolicyByIdQuery(String policyId, String tenantId) {
}
