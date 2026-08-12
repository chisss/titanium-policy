package com.titanium.policy.query.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.titanium.policy.query.view.PolicyInsuredView;

/**
 * 保单被保险人读模型仓储（CQRS 读侧）
 * <p>
 * 支持投影幂等（先按 policyId+tenantId 删除再批量插入）和查询。所有查询强制携带 tenantId 保证多租户隔离。
 * </p>
 */
@Repository
public interface PolicyInsuredViewRepository extends JpaRepository<PolicyInsuredView, String> {

    /**
     * 按保单ID + 租户ID 查询被保险人列表
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 被保险人读模型列表
     */
    List<PolicyInsuredView> findByPolicyIdAndTenantId(String policyId, String tenantId);

    /**
     * 按保单ID + 租户ID 删除（投影幂等用：先清后插）
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     */
    @Modifying
    @Query("DELETE FROM PolicyInsuredView v WHERE v.policyId = :policyId AND v.tenantId = :tenantId")
    void deleteByPolicyIdAndTenantId(String policyId, String tenantId);

    /**
     * 按客户查询其作为<b>被保险人</b>的保单ID列表（支撑「我作为被保险人的保单」查询）。
     *
     * @param customerId 客户ID
     * @param tenantId   租户ID
     * @return 被保险人记录列表
     */
    List<PolicyInsuredView> findByCustomerIdAndTenantId(String customerId, String tenantId);
}
