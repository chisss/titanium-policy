package com.titanium.policy.repository;

import com.titanium.policy.aggregate.Proposal;
import com.titanium.policy.valueobject.proposal.ProposalStatus;

import java.util.Optional;

/**
 * 投保意向单仓库接口
 * <p>
 * 定义投保意向单聚合根的存储和检索方法
 * </p>
 */
public interface ProposalRepository {
    /**
     * 根据ID查询投保意向单
     *
     * @param proposalId 意向单ID
     * @param tenantId   租户ID
     * @return 投保意向单聚合根
     */
    Optional<Proposal> findById(String proposalId, String tenantId);

    /**
     * 保存投保意向单
     *
     * @param proposal 投保意向单聚合根
     * @return 保存后的投保意向单聚合根
     */
    Proposal save(Proposal proposal);

    /**
     * 删除投保意向单
     *
     * @param proposalId 意向单ID
     * @param tenantId   租户ID
     */
    void deleteById(String proposalId, String tenantId);

    /**
     * 根据状态查询投保意向单
     *
     * @param tenantId   租户ID
     * @param statusCode 状态编码
     * @return 投保意向单迭代器
     */
    Iterable<Proposal> findByStatus(String tenantId, ProposalStatus.StatusCode statusCode);

    /**
     * 根据意向单编号查询投保意向单
     *
     * @param proposalNo 意向单编号
     * @param tenantId   租户ID
     * @return 投保意向单聚合根
     */
    Optional<Proposal> findByProposalNo(String proposalNo, String tenantId);

    /**
     * 根据客户ID查询投保意向单
     *
     * @param customerId 客户ID
     * @param tenantId   租户ID
     * @return 投保意向单迭代器
     */
    Iterable<Proposal> findByCustomerId(String customerId, String tenantId);
}