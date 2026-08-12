package com.titanium.policy.query.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.titanium.policy.query.view.PolicyCollectionView;

/**
 * 保单收费信息读模型仓储
 */
@Repository
public interface PolicyCollectionViewRepository extends JpaRepository<PolicyCollectionView, String> {

    /**
     * 按保单查询收费信息（一保单一行）。
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 收费信息；未开单时返回空
     */
    Optional<PolicyCollectionView> findByPolicyIdAndTenantId(String policyId, String tenantId);

    /**
     * 按账单反查收费信息（billing 收讫回调时定位保单）。
     *
     * @param billId   账单ID
     * @param tenantId 租户ID
     * @return 收费信息
     */
    Optional<PolicyCollectionView> findByBillIdAndTenantId(String billId, String tenantId);

    /**
     * 按支付单反查收费信息（payment 支付回调时定位保单）。
     *
     * @param paymentOrderId 支付单ID
     * @param tenantId       租户ID
     * @return 收费信息
     */
    Optional<PolicyCollectionView> findByPaymentOrderIdAndTenantId(String paymentOrderId, String tenantId);
}
