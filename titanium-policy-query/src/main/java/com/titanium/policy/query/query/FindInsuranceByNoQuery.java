package com.titanium.policy.query.query;

/**
 * 根据投保单编号查询投保单（读模型）
 */
public record FindInsuranceByNoQuery(String insuranceNo, String tenantId) {
}
