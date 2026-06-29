package com.titanium.policy.query;

/**
 * 投保单查询类，用于传递查询参数
 */
public record InsuranceQuery(String applicationId, String tenantId) {
}
