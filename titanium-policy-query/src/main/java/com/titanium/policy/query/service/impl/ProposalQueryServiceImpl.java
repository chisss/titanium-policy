package com.titanium.policy.query.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.policy.query.repository.ProposalViewRepository;
import com.titanium.policy.query.result.ProposalQueryResult;
import com.titanium.policy.query.service.ProposalQueryService;
import com.titanium.policy.query.view.ProposalView;
import com.titanium.policy.valueobject.proposal.ProposalStatus;

import jakarta.persistence.criteria.Predicate;
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

    @Override
    @Transactional(readOnly = true)
    public List<ProposalQueryResult> findProposalsByConditions(String proposalNo, String expectedProductCode,
                                                               String status, String tenantId, int page, int size) {
        Specification<ProposalView> spec = buildSpec(proposalNo, expectedProductCode, status, tenantId);
        Pageable pageable = PageRequest.of(Math.max(page, 0), size <= 0 ? 20 : size);
        return proposalViewRepository.findAll(spec, pageable)
                .stream()
                .map(this::toQueryResult)
                .toList();
    }

    /**
     * 构建多条件动态查询规约（仅对非空条件追加谓词）
     */
    private Specification<ProposalView> buildSpec(String proposalNo, String expectedProductCode, String status,
                                                   String tenantId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // 多租户隔离：强制条件
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (isNotBlank(proposalNo)) {
                predicates.add(cb.like(root.get("proposalNo"), "%" + proposalNo + "%"));
            }
            if (isNotBlank(expectedProductCode)) {
                predicates.add(cb.equal(root.get("expectedProductCode"), expectedProductCode));
            }
            if (isNotBlank(status)) {
                try {
                    ProposalStatus.StatusCode statusEnum = ProposalStatus.StatusCode.valueOf(status);
                    predicates.add(cb.equal(root.get("status"), statusEnum));
                } catch (IllegalArgumentException e) {
                    log.warn("无效的意向单状态值: {}", status);
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
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

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
