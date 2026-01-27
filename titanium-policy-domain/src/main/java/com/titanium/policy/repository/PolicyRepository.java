package com.titanium.policy.repository;

import java.util.Optional;

import com.titanium.policy.aggregate.Policy;
import com.titanium.policy.valueobject.PolicyStatus;

public interface PolicyRepository {
    Optional<Policy> findById(String policyId, String tenantId);

    Policy save(Policy policy);

    void deleteById(String policyId, String tenantId);

    /**
     * 根据状态查询保单
     * 
     * @param tenantId 租户ID
     * @param statusCode 状态编码
     * @return 保单迭代器
     */
    Iterable<Policy> findByStatus(String tenantId, PolicyStatus.StatusCode statusCode);

    /**
     * 根据保单编号查询保单
     * 
     * @param policyNo 保单编号
     * @param tenantId 租户ID
     * @return 保单聚合根
     */
    Optional<Policy> findByPolicyNo(String policyNo, String tenantId);

    /**
     * 根据关联投保单ID查询保单
     * 
     * @param applicationId 投保单ID
     * @param tenantId 租户ID
     * @return 保单聚合根
     */
    Optional<Policy> findByApplicationId(String applicationId, String tenantId);

    /**
     * 根据投保人ID查询保单
     * 
     * @param policyHolderId 投保人ID
     * @param tenantId 租户ID
     * @return 保单迭代器
     */
    Iterable<Policy> findByPolicyHolderId(String policyHolderId, String tenantId);
}
