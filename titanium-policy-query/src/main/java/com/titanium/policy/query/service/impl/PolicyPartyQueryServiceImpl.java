package com.titanium.policy.query.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.policy.query.mapper.PolicyLineQueryMapper;
import com.titanium.policy.query.repository.PolicyBeneficiaryViewRepository;
import com.titanium.policy.query.repository.PolicyInsuredViewRepository;
import com.titanium.policy.query.result.PolicyBeneficiaryQueryResult;
import com.titanium.policy.query.result.PolicyInsuredQueryResult;
import com.titanium.policy.query.service.PolicyPartyQueryService;

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
    // View → QueryResult 统一走 PolicyLineQueryMapper：与保单全景查询同源。
    // 🔴 禁止改回手工逐字段 set——同一语义两份实现必然分叉（g12-06 真机探针曾暴露
    // 手工版漏映射 insuredId，增减员定位键在参与方查询口恒 null）。
    private final PolicyLineQueryMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<PolicyInsuredQueryResult> findInsuredByPolicyId(String policyId, String tenantId) {
        return insuredViewRepository.findByPolicyIdAndTenantId(policyId, tenantId)
                .stream()
                .map(mapper::toInsuredResult)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PolicyBeneficiaryQueryResult> findBeneficiariesByPolicyId(String policyId, String tenantId) {
        return beneficiaryViewRepository.findByPolicyIdAndTenantId(policyId, tenantId)
                .stream()
                .map(mapper::toBeneficiaryResult)
                .toList();
    }
}
