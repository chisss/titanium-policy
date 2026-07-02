package com.titanium.policy.query.query;

/**
 * 根据ID查询投保单（读模型）
 */
public record FindInsuranceByIdQuery(String insuranceId, String tenantId) {
}
