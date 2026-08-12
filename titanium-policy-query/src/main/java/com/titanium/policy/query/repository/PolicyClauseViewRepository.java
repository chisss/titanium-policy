package com.titanium.policy.query.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.titanium.policy.query.view.PolicyClauseView;

/**
 * 保单条款快照读模型仓储（L2.5）
 */
@Repository
public interface PolicyClauseViewRepository extends JpaRepository<PolicyClauseView, String> {

    /**
     * 按保单查询全部条款快照（跨险种段）。
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 条款快照列表
     */
    List<PolicyClauseView> findByPolicyIdAndTenantId(String policyId, String tenantId);

    /**
     * 按险种段查询其绑定条款。
     *
     * @param policyProductId 险种段ID
     * @param tenantId        租户ID
     * @return 条款快照列表
     */
    List<PolicyClauseView> findByPolicyProductIdAndTenantId(String policyProductId, String tenantId);
}
