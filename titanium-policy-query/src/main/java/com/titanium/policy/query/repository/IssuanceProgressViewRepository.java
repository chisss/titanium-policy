package com.titanium.policy.query.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * 按意向单ID反查三步出单进度。
     */
    Optional<IssuanceProgressView> findByProposalIdAndTenantId(String proposalId, String tenantId);

    /**
     * 将尚未产生任何业务单据的受理基线原子更新为拒绝终态。
     *
     * @return 更新行数；0 表示事件投影已经推进，调用方不得覆盖
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE IssuanceProgressView v
               SET v.currentStage = :rejectedStage,
                   v.rejectCode = :rejectCode,
                   v.rejectReason = :rejectReason,
                   v.updateTime = :updateTime
             WHERE v.bizNo = :bizNo
               AND v.tenantId = :tenantId
               AND v.currentStage = :acceptedStage
               AND v.proposalId IS NULL
               AND v.insuranceId IS NULL
               AND v.policyId IS NULL
               AND v.underwritingId IS NULL
               AND v.billId IS NULL
               AND v.paymentOrderId IS NULL
            """)
    int markUntouchedBaselineRejected(@Param("bizNo") String bizNo, @Param("tenantId") String tenantId,
            @Param("acceptedStage") String acceptedStage, @Param("rejectedStage") String rejectedStage,
            @Param("rejectCode") String rejectCode, @Param("rejectReason") String rejectReason,
            @Param("updateTime") LocalDateTime updateTime);

    /**
     * 删除尚未产生任何业务单据的纯受理基线，使技术失败可使用同一业务流水号重试。
     *
     * @return 删除行数；0 表示事件投影已经推进，调用方必须保留进度
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM IssuanceProgressView v
             WHERE v.bizNo = :bizNo
               AND v.tenantId = :tenantId
               AND v.currentStage = :acceptedStage
               AND v.proposalId IS NULL
               AND v.insuranceId IS NULL
               AND v.policyId IS NULL
               AND v.underwritingId IS NULL
               AND v.billId IS NULL
               AND v.paymentOrderId IS NULL
            """)
    int deleteUntouchedAcceptedBaseline(@Param("bizNo") String bizNo, @Param("tenantId") String tenantId,
            @Param("acceptedStage") String acceptedStage);
}
