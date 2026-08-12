package com.titanium.policy.query.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.titanium.policy.query.view.PolicyCoverageView;

/**
 * 保单保险责任读模型仓储（L4）
 * <p>
 * 理赔域按此查询保单责任清单以定责（责任保额、免赔、赔付比例、责任级等待期）。
 * </p>
 */
@Repository
public interface PolicyCoverageViewRepository extends JpaRepository<PolicyCoverageView, String> {

    /**
     * 按保单查询全部保险责任（跨险种段）。
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 责任列表
     */
    List<PolicyCoverageView> findByPolicyIdAndTenantId(String policyId, String tenantId);

    /**
     * 按险种段查询其保险责任。
     *
     * @param policyProductId 险种段ID
     * @param tenantId        租户ID
     * @return 责任列表
     */
    List<PolicyCoverageView> findByPolicyProductIdAndTenantId(String policyProductId, String tenantId);

    /**
     * 按挂载对象查询责任（挂标的的车损险 / 挂段的三者险）。
     *
     * @param attachLevel 挂载层级码（LINE/SUBJECT）
     * @param attachRefId 挂载对象ID
     * @param tenantId    租户ID
     * @return 责任列表
     */
    List<PolicyCoverageView> findByAttachLevelAndAttachRefIdAndTenantId(String attachLevel, String attachRefId,
                                                                       String tenantId);
}
