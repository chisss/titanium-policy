package com.titanium.policy.query.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.policy.query.repository.PolicyBeneficiaryViewRepository;
import com.titanium.policy.query.repository.PolicyInsuredViewRepository;
import com.titanium.policy.query.result.PolicyBeneficiaryQueryResult;
import com.titanium.policy.query.result.PolicyInsuredQueryResult;
import com.titanium.policy.query.service.PolicyPartyQueryService;
import com.titanium.policy.query.view.PolicyBeneficiaryView;
import com.titanium.policy.query.view.PolicyInsuredView;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单参与方查询服务实现（CQRS 读侧）
 * <p>
 * 查询由事件投影维护的被保险人/受益人读模型，实现真正的读写分离，
 * 所有查询强制携带 tenantId 保证多租户隔离。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyPartyQueryServiceImpl implements PolicyPartyQueryService {

    private final PolicyInsuredViewRepository   insuredViewRepository;
    private final PolicyBeneficiaryViewRepository beneficiaryViewRepository;

    @Override
    @Transactional(readOnly = true)
    public List<PolicyInsuredQueryResult> findInsuredByPolicyId(String policyId, String tenantId) {
        return insuredViewRepository.findByPolicyIdAndTenantId(policyId, tenantId)
                .stream()
                .map(this::toInsuredResult)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PolicyBeneficiaryQueryResult> findBeneficiariesByPolicyId(String policyId, String tenantId) {
        return beneficiaryViewRepository.findByPolicyIdAndTenantId(policyId, tenantId)
                .stream()
                .map(this::toBeneficiaryResult)
                .toList();
    }

    /** View → 被保险人查询结果 */
    private PolicyInsuredQueryResult toInsuredResult(PolicyInsuredView view) {
        PolicyInsuredQueryResult result = new PolicyInsuredQueryResult();
        result.setPolicyId(view.getPolicyId());
        result.setCustomerId(view.getCustomerId());
        result.setInsuredName(view.getInsuredName());
        result.setFamilyRelation(view.getFamilyRelation());
        result.setTenantId(view.getTenantId());
        return result;
    }

    /** View → 受益人查询结果 */
    private PolicyBeneficiaryQueryResult toBeneficiaryResult(PolicyBeneficiaryView view) {
        PolicyBeneficiaryQueryResult result = new PolicyBeneficiaryQueryResult();
        result.setPolicyId(view.getPolicyId());
        result.setCustomerId(view.getCustomerId());
        result.setBeneficiaryName(view.getBeneficiaryName());
        result.setBeneficiaryType(view.getBeneficiaryType());
        result.setOrderNo(view.getOrderNo());
        result.setShareRatio(view.getShareRatio());
        result.setTenantId(view.getTenantId());
        return result;
    }
}
