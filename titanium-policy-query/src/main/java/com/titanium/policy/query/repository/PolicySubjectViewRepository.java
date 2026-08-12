package com.titanium.policy.query.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.titanium.policy.query.view.PolicySubjectView;

/**
 * 保单标的读模型仓储（L3）
 */
@Repository
public interface PolicySubjectViewRepository extends JpaRepository<PolicySubjectView, String> {

    /**
     * 按保单查询全部标的（车险多车、企财多分项时返回多行）。
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 标的列表
     */
    List<PolicySubjectView> findByPolicyIdAndTenantId(String policyId, String tenantId);

    /**
     * 按险种段查询其承保标的。
     *
     * @param policyProductId 险种段ID
     * @param tenantId        租户ID
     * @return 标的列表
     */
    List<PolicySubjectView> findByPolicyProductIdAndTenantId(String policyProductId, String tenantId);

    /**
     * 按客户查询其作为标的（被保险人）的记录，支撑「某客户被哪些保单承保」查询。
     *
     * @param customerId 客户ID
     * @param tenantId   租户ID
     * @return 标的列表
     */
    List<PolicySubjectView> findByCustomerIdAndTenantId(String customerId, String tenantId);
}
