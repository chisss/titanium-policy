package com.titanium.policy.infrastructure.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.titanium.policy.aggregate.Proposal;
import com.titanium.policy.infrastructure.entity.ProposalEntity;
import com.titanium.policy.infrastructure.mapper.ProposalMapper;
import com.titanium.policy.infrastructure.repository.jpa.JpaProposalRepository;
import com.titanium.policy.repository.ProposalRepository;
import com.titanium.policy.valueobject.proposal.ProposalStatus;

/**
 * 投保意向单仓库实现
 * <p>
 * 使用JPA repository来访问和操作投保意向单数据
 * </p>
 */
@Repository
public class ProposalRepositoryImpl implements ProposalRepository {
    private final JpaProposalRepository jpaProposalRepository;
    private final ProposalMapper proposalMapper;

    /**
     * 构造函数
     *
     * @param jpaProposalRepository JPA投保意向单仓库
     * @param proposalMapper 投保意向单映射器
     */
    public ProposalRepositoryImpl(JpaProposalRepository jpaProposalRepository, ProposalMapper proposalMapper) {
        this.jpaProposalRepository = jpaProposalRepository;
        this.proposalMapper = proposalMapper;
    }

    @Override
    public Optional<Proposal> findById(String proposalId, String tenantId) {
        Optional<ProposalEntity> entityOpt = jpaProposalRepository.findById(proposalId);
        return entityOpt.map(proposalMapper::toAggregate);
    }

    @Override
    public Proposal save(Proposal proposal) {
        ProposalEntity entity = proposalMapper.toEntity(proposal);
        ProposalEntity savedEntity = jpaProposalRepository.save(entity);
        return proposalMapper.toAggregate(savedEntity);
    }

    @Override
    public void deleteById(String proposalId, String tenantId) {
        jpaProposalRepository.deleteById(proposalId);
    }

    @Override
    public Iterable<Proposal> findByStatus(String tenantId, ProposalStatus.StatusCode statusCode) {
        Iterable<ProposalEntity> entities = jpaProposalRepository.findByStatusCodeAndTenantId(statusCode, tenantId);
        List<Proposal> proposals = new ArrayList<>();
        for (ProposalEntity entity : entities) {
            proposals.add(proposalMapper.toAggregate(entity));
        }
        return proposals;
    }

    @Override
    public Optional<Proposal> findByProposalNo(String proposalNo, String tenantId) {
        Optional<ProposalEntity> entityOpt = jpaProposalRepository.findByProposalNoAndTenantId(proposalNo, tenantId);
        return entityOpt.map(proposalMapper::toAggregate);
    }

    @Override
    public Iterable<Proposal> findByCustomerId(String customerId, String tenantId) {
        Iterable<ProposalEntity> entities = jpaProposalRepository.findByCustomerIdAndTenantId(customerId, tenantId);
        List<Proposal> proposals = new ArrayList<>();
        for (ProposalEntity entity : entities) {
            proposals.add(proposalMapper.toAggregate(entity));
        }
        return proposals;
    }
}
