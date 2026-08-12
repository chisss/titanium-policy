package com.titanium.policy.query.query;

import com.titanium.metadata.enums.customer.CustomerEnum.InsuranceRole;

/**
 * 按客户与保险角色查询其名下保单
 * <p>
 * 取代仅按投保人查询的局限：同一客户可能是 A 保单的投保人、B 保单的被保险人、C 保单的受益人，
 * 三种角色下「我的保单」含义不同。
 * </p>
 *
 * @param customerId 客户ID
 * @param role       保险角色（投保人 / 被保险人 / 受益人；为 null 时返回三种角色的并集）
 * @param tenantId   租户ID
 * @param page       页码（0 起）
 * @param size       每页条数
 */
public record FindPoliciesByCustomerRoleQuery(String customerId, InsuranceRole role, String tenantId, int page,
                                              int size) {
}
