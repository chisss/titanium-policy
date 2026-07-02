package com.titanium.policy.query.query;

/**
 * 根据意向单编号查询投保意向单（读模型）
 */
public record FindProposalByNoQuery(String proposalNo, String tenantId) {
}
