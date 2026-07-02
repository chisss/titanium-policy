package com.titanium.policy.query.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.titanium.policy.query.view.PolicyRelationView;

/**
 * 保单父子关系读模型仓储
 * <p>
 * 支撑父保单状态级联：按父保单ID查询其全部子保单。所有查询强制携带 tenantId 实现多租户隔离。
 * </p>
 */
@Repository
public interface PolicyRelationViewRepository extends JpaRepository<PolicyRelationView, String> {

    /**
     * 查询某父保单下的全部子保单（多租户隔离）
     *
     * @param parentPolicyId 父保单ID
     * @param tenantId 租户ID
     * @return 子保单关系列表
     */
    List<PolicyRelationView> findByParentPolicyIdAndTenantId(String parentPolicyId, String tenantId);
}
