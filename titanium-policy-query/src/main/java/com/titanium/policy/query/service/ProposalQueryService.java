package com.titanium.policy.query.service;

import com.titanium.policy.query.result.ProposalQueryResult;

/**
 * 投保意向单查询服务
 * <p>
 * 提供意向单读模型查询功能（CQRS 读侧）。
 * </p>
 */
public interface ProposalQueryService {

    /**
     * 根据ID查询意向单
     *
     * @param proposalId 意向单ID
     * @param tenantId 租户ID
     * @return 意向单查询结果，不存在时为 null
     */
    ProposalQueryResult findProposalById(String proposalId, String tenantId);

    /**
     * 根据编号查询意向单
     *
     * @param proposalNo 意向单编号
     * @param tenantId 租户ID
     * @return 意向单查询结果，不存在时为 null
     */
    ProposalQueryResult findProposalByNo(String proposalNo, String tenantId);
}
