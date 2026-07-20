package com.titanium.policy.query.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.policy.query.repository.PolicyEndorsementViewRepository;
import com.titanium.policy.query.result.PolicyEndorsementQueryResult;
import com.titanium.policy.query.service.PolicyEndorsementQueryService;
import com.titanium.policy.query.view.PolicyEndorsementView;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单批改历史查询服务实现（CQRS 读侧）
 * <p>
 * 查询由 PolicyEndorsedEvent 投影维护的批改流水读模型 {@code t_policy_endorsement_view}，
 * 所有查询强制携带 tenantId 保证多租户隔离。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyEndorsementQueryServiceImpl implements PolicyEndorsementQueryService {

    private final PolicyEndorsementViewRepository endorsementViewRepository;

    @Override
    @Transactional(readOnly = true)
    public List<PolicyEndorsementQueryResult> findEndorsementsByPolicyId(String policyId, String tenantId) {
        return endorsementViewRepository.findByPolicyIdAndTenantId(policyId, tenantId)
                .stream()
                .map(this::toQueryResult)
                .toList();
    }

    /** View → 批改历史查询结果 */
    private PolicyEndorsementQueryResult toQueryResult(PolicyEndorsementView view) {
        PolicyEndorsementQueryResult result = new PolicyEndorsementQueryResult();
        result.setEndorsementNo(view.getEndorsementNo());
        result.setPolicyId(view.getPolicyId());
        result.setUpdateType(view.getUpdateType());
        result.setCategory(view.getCategory());
        result.setPolicyVersion(view.getPolicyVersion());
        result.setEffectiveDate(view.getEffectiveDate());
        result.setChangeSummary(view.getChangeSummary());
        result.setRequiresPremiumRecalc(view.isRequiresPremiumRecalc());
        result.setSourceMaintenanceId(view.getSourceMaintenanceId());
        result.setOperatorId(view.getOperatorId());
        result.setEndorsedAt(view.getEndorsedAt());
        result.setTenantId(view.getTenantId());
        return result;
    }
}
