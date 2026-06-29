package com.titanium.policy.query.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.titanium.policy.query.entity.PolicyEndorsementView;

/**
 * 批单读模型仓储
 * <p>
 * 支撑按保单查批改流水。所有查询强制携带 tenantId 实现多租户隔离。
 * </p>
 */
@Repository
public interface PolicyEndorsementViewRepository extends JpaRepository<PolicyEndorsementView, String> {

    /**
     * 按保单ID查批改流水（多租户隔离）
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 批单列表
     */
    List<PolicyEndorsementView> findByPolicyIdAndTenantId(String policyId, String tenantId);
}
