package com.titanium.policy.query.query;

/**
 * 多条件分页查询投保意向单列表
 *
 * @param proposalNo      意向单编号（模糊匹配，可空）
 * @param customerId      客户ID（精确匹配，可空）
 * @param expectedProductCode 期望险种编码（精确匹配，可空）
 * @param status          意向单状态（枚举 name，可空）
 * @param tenantId        租户ID
 * @param page            页码（从0开始）
 * @param size            每页条数
 */
public record FindProposalsByConditionQuery(
        String proposalNo,
        String customerId,
        String expectedProductCode,
        String status,
        String tenantId,
        int page,
        int size
) {
}
