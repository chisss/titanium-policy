package com.titanium.policy.query.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.titanium.policy.query.view.AnnuityPayoutPlanView;

/**
 * 年金给付计划读模型仓储（CQRS 读侧）
 * <p>
 * 年金计划与保单 1:1，主键即保单ID，投影按主键幂等 upsert。面向用户的查询强制携带 tenantId 实现多租户隔离；
 * 系统定时批处理（年金逐期给付）按「到期日 + 给付中」跨租户扫描到期计划，再以每行自带 tenantId 逐条派发。
 * </p>
 */
@Repository
public interface AnnuityPayoutPlanViewRepository extends JpaRepository<AnnuityPayoutPlanView, String> {

    /**
     * 按保单ID查询年金给付计划（多租户隔离）
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 年金给付计划读模型
     */
    Optional<AnnuityPayoutPlanView> findByPolicyIdAndTenantId(String policyId, String tenantId);

    /**
     * 查询到期待给付的年金计划（系统定时批处理用，跨租户分页扫描）。
     * <p>
     * 命中条件：给付状态为「给付中」且下一给付日不晚于给付基准日。给付后投影推进
     * {@code nextPayoutDate} 前移，天然幂等——同一期不会被重复选中（依赖读模型最终一致，日级调度足够收敛）。
     * </p>
     *
     * @param payoutStatus 给付状态码（传 {@code PAYING}）
     * @param dueDate 给付基准日（含）
     * @param pageable 分页参数（按主键稳定排序，游标翻页）
     * @return 到期待给付的年金计划列表
     */
    List<AnnuityPayoutPlanView> findByPayoutStatusAndNextPayoutDateLessThanEqual(String payoutStatus,
            LocalDateTime dueDate, Pageable pageable);
}
