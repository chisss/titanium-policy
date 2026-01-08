package com.titanium.policy.repository;

import java.util.Optional;

import com.titanium.metadata.enums.PolicyEnum.PolicyStatus;
import com.titanium.policy.aggregate.Policy;

public interface PolicyRepository {
    Optional<Policy> findById(String policyId, String tenantId);

    Policy save(Policy policy);

    void deleteById(String policyId, String tenantId);

    /**
     * 根据状态列表查询保单
     * 
     * @param tenantId 租户ID
     * @param statuses 状态列表
     * @return 保单迭代器
     */
    Iterable<Policy> findByStatusIn(String tenantId, PolicyStatus... statuses);
}
