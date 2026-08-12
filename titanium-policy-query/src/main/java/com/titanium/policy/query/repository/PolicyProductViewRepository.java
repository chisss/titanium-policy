package com.titanium.policy.query.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.titanium.policy.query.view.PolicyProductView;

/**
 * 保单险种段读模型仓储（L2）
 * <p>
 * 一保单多段，按保单查询时返回该保单全部险种段（主险 + 附加险）。
 * </p>
 */
@Repository
public interface PolicyProductViewRepository extends JpaRepository<PolicyProductView, String> {

    /**
     * 按保单查询全部险种段（按段序号升序，主险通常为首段）。
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 险种段列表
     */
    List<PolicyProductView> findByPolicyIdAndTenantIdOrderByLineNoAsc(String policyId, String tenantId);

    /**
     * 按产品查询关联的险种段（产品维度统计：该产品承保了多少段）。
     *
     * @param productId 产品ID
     * @param tenantId  租户ID
     * @return 险种段列表
     */
    List<PolicyProductView> findByProductIdAndTenantId(String productId, String tenantId);
}
