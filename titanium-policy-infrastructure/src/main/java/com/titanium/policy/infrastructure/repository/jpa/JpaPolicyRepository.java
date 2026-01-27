package com.titanium.policy.infrastructure.repository.jpa;

import com.titanium.policy.infrastructure.entity.PolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 正式保单JPA仓库接口
 * <p>
 * 用于访问和操作t_policy表中的数据
 * </p>
 */
public interface JpaPolicyRepository extends JpaRepository<PolicyEntity, String> {
    /**
     * 根据保单号查询保单
     *
     * @param policyNo 保单编号
     * @param tenantId 租户ID
     * @return 正式保单实体
     */
    Optional<PolicyEntity> findByPolicyNoAndTenantId(String policyNo, String tenantId);

    /**
     * 根据关联投保单ID查询保单
     *
     * @param applicationId 投保单ID
     * @param tenantId      租户ID
     * @return 正式保单实体
     */
    Optional<PolicyEntity> findByApplicationIdAndTenantId(String applicationId, String tenantId);

    /**
     * 根据保单状态查询保单
     *
     * @param policyStatus 保单状态
     * @param tenantId     租户ID
     * @return 正式保单实体列表
     */
    Iterable<PolicyEntity> findByPolicyStatusAndTenantId(String policyStatus, String tenantId);

    /**
     * 根据投保人ID查询保单
     *
     * @param policyHolderId 投保人ID
     * @param tenantId       租户ID
     * @return 正式保单实体列表
     */
    Iterable<PolicyEntity> findByPolicyHolderIdAndTenantId(String policyHolderId, String tenantId);
}