package com.titanium.policy.query.query;

/**
 * 保单标的清单查询（L3）
 * <p>
 * 车险多车、企财险多分项时返回多个标的，各含类型化属性包。
 * </p>
 *
 * @param policyId 保单ID
 * @param tenantId 租户ID
 */
public record FindPolicySubjectsQuery(String policyId, String tenantId) {
}
