package com.titanium.policy.query.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.policy.query.repository.InsuranceViewRepository;
import com.titanium.policy.query.result.InsuranceQueryResult;
import com.titanium.policy.query.service.InsuranceQueryService;
import com.titanium.policy.query.view.InsuranceView;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 投保单查询服务实现（CQRS 读侧）
 * <p>
 * 查询由事件投影维护的读模型表 {@code t_insurance_view}，实现真正的读写分离。 所有查询强制携带
 * {@code tenantId} 保证多租户隔离。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsuranceQueryServiceImpl implements InsuranceQueryService {

    private final InsuranceViewRepository insuranceViewRepository;

    @Override
    @Transactional(readOnly = true)
    public InsuranceQueryResult findInsuranceById(String insuranceId, String tenantId) {
        return insuranceViewRepository.findByInsuranceIdAndTenantId(insuranceId, tenantId)
                .map(this::toQueryResult)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public InsuranceQueryResult findInsuranceByNo(String insuranceNo, String tenantId) {
        return insuranceViewRepository.findByInsuranceNoAndTenantId(insuranceNo, tenantId)
                .map(this::toQueryResult)
                .orElse(null);
    }

    /**
     * 读模型实体 → 查询结果
     */
    private InsuranceQueryResult toQueryResult(InsuranceView view) {
        InsuranceQueryResult result = new InsuranceQueryResult();
        result.setInsuranceId(view.getInsuranceId());
        result.setInsuranceNo(view.getInsuranceNo());
        result.setProposalId(view.getProposalId());
        result.setPolicyForm(view.getPolicyForm());
        result.setHolderId(view.getHolderId());
        result.setInsuredCount(view.getInsuredCount());
        result.setExactPremium(view.getExactPremium());
        result.setCurrency(view.getCurrency());
        result.setInsurancePeriodStart(view.getInsurancePeriodStart());
        result.setInsurancePeriodEnd(view.getInsurancePeriodEnd());
        result.setStatus(view.getStatus());
        result.setUnderwritingResultCode(view.getUnderwritingResultCode());
        result.setUnderwritingId(view.getUnderwritingId());
        result.setIssuedTime(view.getIssuedTime());
        result.setCreateTime(view.getCreateTime());
        result.setUpdateTime(view.getUpdateTime());
        result.setTenantId(view.getTenantId());
        return result;
    }
}
