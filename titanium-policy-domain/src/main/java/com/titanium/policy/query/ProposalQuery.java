package com.titanium.policy.query;

/**
 * 投保意向单查询类，用于传递查询参数
 */
public record ProposalQuery(String proposalId, String tenantId) {
}
