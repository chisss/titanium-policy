package com.titanium.policy.query.query;

/**
 * 保单险种段清单查询（L2）
 * <p>
 * 供保全按段操作（附加险单独加退保）、佣金与再保按段拆分口径使用。
 * </p>
 *
 * @param policyId     保单ID
 * @param withDetails  是否装配段内条款/标的/责任明细（false 时仅返回段本身，减少查询开销）
 * @param tenantId     租户ID
 */
public record FindPolicyLinesQuery(String policyId, boolean withDetails, String tenantId) {
}
