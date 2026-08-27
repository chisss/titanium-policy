package com.titanium.policy.query.service;

import java.util.List;

import org.springframework.data.domain.Page;

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

    /**
     * 多条件分页查询意向单列表
     *
     * @param proposalNo          意向单编号（模糊，可空）
     * @param customerId          客户ID（精确，可空）
     * @param expectedProductCode 险种编码（精确，可空）
     * @param status              状态枚举 name（可空）
     * @param tenantId            租户ID
     * @param page                页码（从0开始）
     * @param size                每页条数
     * @return 意向单查询结果列表
     */
    List<ProposalQueryResult> findProposalsByConditions(String proposalNo, String customerId,
                                                        String expectedProductCode, String status, String tenantId,
                                                        int page, int size);

    /**
     * 多条件分页查询意向单，并保留总条数等分页元数据。
     *
     * @return 意向单分页查询结果
     */
    Page<ProposalQueryResult> findProposalsPageByConditions(String proposalNo, String customerId,
                                                            String expectedProductCode, String status,
                                                            String tenantId, int page, int size);
}
