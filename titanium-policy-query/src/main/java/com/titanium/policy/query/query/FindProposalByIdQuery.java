package com.titanium.policy.query.query;

/**
 * 根据ID查询投保意向单（读模型）
 */
public record FindProposalByIdQuery(String proposalId, String tenantId) {
}
