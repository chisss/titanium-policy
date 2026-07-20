package com.titanium.policy.query.query;

/**
 * 多条件分页查询投保单列表
 *
 * @param insuranceNo 投保单编号（模糊匹配，可空）
 * @param holderId    投保人ID（精确匹配，可空）
 * @param productCode 险种编码（可空）
 * @param status      投保单状态（枚举 name，可空）
 * @param tenantId    租户ID
 * @param page        页码（从0开始）
 * @param size        每页条数
 */
public record FindInsurancesByConditionQuery(
        String insuranceNo,
        String holderId,
        String productCode,
        String status,
        String tenantId,
        int page,
        int size
) {
}
