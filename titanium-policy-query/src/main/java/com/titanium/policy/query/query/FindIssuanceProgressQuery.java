package com.titanium.policy.query.query;

/**
 * 出单进度查询
 * <p>
 * 两步/三步出单为异步长流程（含核保与收费），调用方据此轮询当前阶段与各单据ID。
 * </p>
 *
 * @param bizNo    业务流水号
 * @param tenantId 租户ID
 */
public record FindIssuanceProgressQuery(String bizNo, String tenantId) {
}
