package com.titanium.policy.query.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.policy.query.repository.ProposalViewRepository;
import com.titanium.policy.query.result.ProposalQueryResult;
import com.titanium.policy.query.service.ProposalQueryService;
import com.titanium.policy.query.view.ProposalView;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 投保意向单查询服务实现（CQRS 读侧）
 * <p>
 * 查询由事件投影维护的读模型表 {@code t_proposal_view}，实现真正的读写分离。 所有查询强制携带
 * {@code tenantId} 保证多租户隔离。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProposalQueryServiceImpl implements ProposalQueryService {

    private final ProposalViewRepository proposalViewRepository;

    @Override
    @Transactional(readOnly = true)
    public ProposalQueryResult findProposalById(String proposalId, String tenantId) {
        return proposalViewRepository.findByProposalIdAndTenantId(proposalId, tenantId)
                .map(this::toQueryResult)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public ProposalQueryResult findProposalByNo(String proposalNo, String tenantId) {
        return proposalViewRepository.findByProposalNoAndTenantId(proposalNo, tenantId)
                .map(this::toQueryResult)
                .orElse(null);
    }

    /**
     * 读模型实体 → 查询结果
     */
    private ProposalQueryResult toQueryResult(ProposalView view) {
        ProposalQueryResult result = new ProposalQueryResult();
        result.setProposalId(view.getProposalId());
        result.setProposalNo(view.getProposalNo());
        result.setPolicyForm(view.getPolicyForm());
        result.setChannel(view.getChannel());
        result.setCustomerId(view.getCustomerId());
        result.setIntendedSumInsured(view.getIntendedSumInsured());
        result.setIntendedPremium(view.getIntendedPremium());
        result.setInsurancePeriodStart(view.getInsurancePeriodStart());
        result.setInsurancePeriodEnd(view.getInsurancePeriodEnd());
        result.setExpectedProductCode(view.getExpectedProductCode());
        result.setStatus(view.getStatus());
        result.setCreateTime(view.getCreateTime());
        result.setUpdateTime(view.getUpdateTime());
        result.setTenantId(view.getTenantId());
        return result;
    }
}
