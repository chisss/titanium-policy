package com.titanium.policy.query.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.titanium.policy.query.view.IssuanceProgressView;

/**
 * 出单进度读模型仓储
 * <p>
 * 承载出单幂等查询（按租户 + 业务流水号）与进度查询。
 * </p>
 */
@Repository
public interface IssuanceProgressViewRepository extends JpaRepository<IssuanceProgressView, String> {

    /**
     * 按业务流水号查出单进度（幂等判定与进度查询共用）。
     *
     * @param bizNo    业务流水号
     * @param tenantId 租户ID
     * @return 出单进度；未受理过返回空
     */
    Optional<IssuanceProgressView> findByBizNoAndTenantId(String bizNo, String tenantId);

    /**
     * 按保单ID反查出单进度（保单溯源出单流程用）。
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 出单进度
     */
    Optional<IssuanceProgressView> findByPolicyIdAndTenantId(String policyId, String tenantId);

    /**
     * 按投保单ID反查出单进度（Saga 推进阶段时回写用）。
     *
     * @param insuranceId 投保单ID
     * @param tenantId    租户ID
     * @return 出单进度
     */
    Optional<IssuanceProgressView> findByInsuranceIdAndTenantId(String insuranceId, String tenantId);
}
