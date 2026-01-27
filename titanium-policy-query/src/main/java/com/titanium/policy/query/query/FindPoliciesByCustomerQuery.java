package com.titanium.policy.query.query;

/**
 * 根据客户ID查询保单
 */
public record FindPoliciesByCustomerQuery(String customerId, String tenantId, int page, int size) {
}