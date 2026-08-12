package com.titanium.policy.query.query;

/**
 * 保单保险责任清单查询（L4）
 * <p>
 * 理赔域定责的数据入口：返回责任保额、免赔、赔付比例与责任级等待期。
 * </p>
 *
 * @param policyId 保单ID
 * @param tenantId 租户ID
 */
public record FindPolicyCoveragesQuery(String policyId, String tenantId) {
}
