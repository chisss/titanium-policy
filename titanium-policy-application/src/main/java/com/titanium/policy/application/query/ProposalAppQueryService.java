package com.titanium.policy.application.query;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.titanium.policy.aggregate.Proposal;
import com.titanium.policy.repository.ProposalRepository;
import com.titanium.policy.valueobject.proposal.ProposalStatus;

import jakarta.annotation.Resource;

/**
 * 投保意向单查询服务
 * <p>
 * 处理投保意向单相关的查询，协调领域层和基础设施层
 * </p>
 */
@Service
public class ProposalAppQueryService {
    @Resource
    private ProposalRepository proposalRepository;

    /**
     * 根据ID查询投保意向单
     *
     * @param proposalId 意向单ID
     * @param tenantId 租户ID
     * @return 投保意向单
     */
    public Optional<Proposal> findById(String proposalId, String tenantId) {
        return proposalRepository.findById(proposalId, tenantId);
    }

    /**
     * 根据状态查询投保意向单
     *
     * @param tenantId 租户ID
     * @param statusCode 状态编码
     * @return 投保意向单列表
     */
    public Iterable<Proposal> findByStatus(String tenantId, ProposalStatus.StatusCode statusCode) {
        return proposalRepository.findByStatus(tenantId, statusCode);
    }

    /**
     * 根据意向单编号查询投保意向单
     *
     * @param proposalNo 意向单编号
     * @param tenantId 租户ID
     * @return 投保意向单
     */
    public Optional<Proposal> findByProposalNo(String proposalNo, String tenantId) {
        return proposalRepository.findByProposalNo(proposalNo, tenantId);
    }

    /**
     * 根据客户ID查询投保意向单
     *
     * @param customerId 客户ID
     * @param tenantId 租户ID
     * @return 投保意向单列表
     */
    public Iterable<Proposal> findByCustomerId(String customerId, String tenantId) {
        return proposalRepository.findByCustomerId(customerId, tenantId);
    }
}
