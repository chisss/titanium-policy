package com.titanium.policy.application.query;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.titanium.policy.aggregate.Policy;
import com.titanium.policy.repository.PolicyRepository;
import com.titanium.policy.valueobject.PolicyStatus;

import jakarta.annotation.Resource;

/**
 * 保单查询服务
 * <p>
 * 处理保单相关的查询，协调领域层和基础设施层
 * </p>
 */
@Service
public class PolicyAppQueryService {
    @Resource
    private PolicyRepository policyRepository;

    /**
     * 根据ID查询保单
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 保单
     */
    public Optional<Policy> findById(String policyId, String tenantId) {
        return policyRepository.findById(policyId, tenantId);
    }

    /**
     * 根据状态查询保单
     *
     * @param tenantId 租户ID
     * @param statusCode 状态编码
     * @return 保单列表
     */
    public Iterable<Policy> findByStatus(String tenantId, PolicyStatus.StatusCode statusCode) {
        return policyRepository.findByStatus(tenantId, statusCode);
    }

    /**
     * 根据保单编号查询保单
     *
     * @param policyNo 保单编号
     * @param tenantId 租户ID
     * @return 保单
     */
    public Optional<Policy> findByPolicyNo(String policyNo, String tenantId) {
        return policyRepository.findByPolicyNo(policyNo, tenantId);
    }

    /**
     * 根据关联投保单ID查询保单
     *
     * @param applicationId 投保单ID
     * @param tenantId 租户ID
     * @return 保单
     */
    public Optional<Policy> findByApplicationId(String applicationId, String tenantId) {
        return policyRepository.findByApplicationId(applicationId, tenantId);
    }

    /**
     * 根据投保人ID查询保单
     *
     * @param policyHolderId 投保人ID
     * @param tenantId 租户ID
     * @return 保单列表
     */
    public Iterable<Policy> findByPolicyHolderId(String policyHolderId, String tenantId) {
        return policyRepository.findByPolicyHolderId(policyHolderId, tenantId);
    }
}
