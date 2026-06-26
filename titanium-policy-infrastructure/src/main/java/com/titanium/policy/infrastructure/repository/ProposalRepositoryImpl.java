package com.titanium.policy.infrastructure.repository;

import com.titanium.policy.aggregate.Proposal;
import com.titanium.policy.infrastructure.entity.ProposalEntity;
import com.titanium.policy.infrastructure.mapper.ProposalMapper;
import com.titanium.policy.infrastructure.repository.jpa.JpaProposalRepository;
import com.titanium.policy.repository.ProposalRepository;
import com.titanium.policy.valueobject.Amount;
import com.titanium.policy.valueobject.proposal.ProposalBasicInfo;
import com.titanium.policy.valueobject.proposal.ProposalStatus;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
            proposals.add(convertToAggregate(entity));
        }
        return proposals;
    }

    @Override
    public Optional<Proposal> findByProposalNo(String proposalNo, String tenantId) {
        Optional<ProposalEntity> entityOpt = jpaProposalRepository.findByProposalNoAndTenantId(proposalNo, tenantId);
        return entityOpt.map(this::convertToAggregate);
    }

    @Override
    public Iterable<Proposal> findByCustomerId(String customerId, String tenantId) {
        Iterable<ProposalEntity> entities = jpaProposalRepository.findByCustomerIdAndTenantId(customerId, tenantId);
        List<Proposal> proposals = new ArrayList<>();
        for (ProposalEntity entity : entities) {
            proposals.add(convertToAggregate(entity));
        }
        return proposals;
    }

    /**
     * 将聚合根转换为数据库实体
     *
     * @param proposal 投保意向单聚合根
     * @return 投保意向单数据库实体
     */
    private ProposalEntity convertToEntity(Proposal proposal) {
        ProposalEntity entity = new ProposalEntity();
        entity.setProposalId(proposal.getProposalId());
        entity.setProposalNo(proposal.getProposalNo());
        entity.setPolicyForm(proposal.getPolicyForm());
        entity.setParentProposalId(proposal.getParentProposalId());
        entity.setChannel(proposal.getChannel());
        entity.setCustomerId(proposal.getBasicInfo().customerId());
        entity.setIntendedSumInsured(proposal.getBasicInfo().intendedSumInsured().value().doubleValue());
        entity.setIntendedPremium(proposal.getBasicInfo().intendedPremium().value().doubleValue());
        entity.setCurrency(proposal.getBasicInfo().intendedPremium().currency());
        entity.setInsurancePeriodStart(proposal.getBasicInfo().insurancePeriodStart());
        entity.setInsurancePeriodEnd(proposal.getBasicInfo().insurancePeriodEnd());
        entity.setExpectedProductCode(proposal.getBasicInfo().expectedProductCode());
        entity.setStatusCode(proposal.getStatus().statusCode());
        entity.setStatusTime(proposal.getStatus().statusTime());
        entity.setChangeReason(proposal.getStatus().changeReason());
        entity.setCreateTime(proposal.getCreateTime());
        entity.setUpdateTime(proposal.getUpdateTime());
        entity.setTenantId(proposal.getTenantId());
        return entity;
    }

    /**
     * 将数据库实体转换为聚合根
     *
     * @param entity 投保意向单数据库实体
     * @return 投保意向单聚合根
     */
    private Proposal convertToAggregate(ProposalEntity entity) {
        ProposalBasicInfo basicInfo = new ProposalBasicInfo(
                entity.getCustomerId(),
                Amount.of(entity.getIntendedSumInsured(), entity.getCurrency()),
                Amount.of(entity.getIntendedPremium(), entity.getCurrency()),
                entity.getInsurancePeriodStart(),
                entity.getInsurancePeriodEnd(),
                entity.getExpectedProductCode()
        );

        ProposalStatus status = new ProposalStatus(
                entity.getStatusCode(),
                entity.getStatusTime(),
                entity.getChangeReason()
        );

        Proposal proposal = Proposal.createDraft(
                entity.getProposalId(),
                entity.getProposalNo(),
                entity.getPolicyForm(),
                entity.getChannel(),
                basicInfo,
                entity.getTenantId()
        );

        // 设置其他属性
        // 这里需要添加申请人和标的信息，但目前的设计中没有对应的实体关系，
        // 后续需要完善这部分逻辑，添加申请人和标的的关联实体和转换逻辑

        return proposal;
    }
}