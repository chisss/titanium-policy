package com.titanium.policy.query.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.titanium.metadata.enums.policy.PolicyEnum;
import com.titanium.policy.query.entity.PolicyView;

/**
 * 保单读模型仓储
 * <p>
 * CQRS 查询侧仓储，直接访问读模型表 {@code t_policy_view}，与写侧仓储隔离。 所有查询方法强制携带 {@code tenantId}
 * 实现多租户数据隔离。 继承 {@link JpaSpecificationExecutor} 以支持多条件动态组合查询。
 * </p>
 */
@Repository
public interface PolicyViewRepository
        extends JpaRepository<PolicyView, String>, JpaSpecificationExecutor<PolicyView> {

    /**
     * 按保单ID + 租户ID查询（多租户隔离）
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 读模型Optional
     */
    Optional<PolicyView> findByPolicyIdAndTenantId(String policyId, String tenantId);

    /**
     * 按保单号 + 租户ID查询
     *
     * @param policyNo 保单号
     * @param tenantId 租户ID
     * @return 读模型Optional
     */
    Optional<PolicyView> findByPolicyNoAndTenantId(String policyNo, String tenantId);

    /**
     * 按投保人ID + 租户ID分页查询
     *
     * @param policyHolderId 投保人ID
     * @param tenantId 租户ID
     * @param pageable 分页参数
     * @return 读模型列表
     */
    List<PolicyView> findByPolicyHolderIdAndTenantId(String policyHolderId, String tenantId, Pageable pageable);

    /**
     * 按状态 + 租户ID分页查询
     *
     * @param policyStatus 保单状态
     * @param tenantId 租户ID
     * @param pageable 分页参数
     * @return 读模型列表
     */
    List<PolicyView> findByPolicyStatusAndTenantId(PolicyEnum.PolicyStatus policyStatus, String tenantId, Pageable pageable);
}
