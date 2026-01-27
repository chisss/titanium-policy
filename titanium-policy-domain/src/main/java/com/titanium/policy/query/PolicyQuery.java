package com.titanium.policy.query;

/**
 * 保单查询类，用于传递查询参数
 */
public record PolicyQuery(String policyId, String tenantId) {
}