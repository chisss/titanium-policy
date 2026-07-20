package com.titanium.policy.query.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyEnum;
import com.titanium.policy.query.view.PolicyView;

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

    /**
     * 查询到期待满期给付的保单（系统定时批处理用，跨租户分页扫描）。
     * <p>
     * 命中条件：状态为生效、险种为指定生存给付型（两全险 ENDOWMENT）、止期不晚于给付基准日。满期给付后
     * 保单转 EXPIRED，投影同步，天然幂等——同一保单不会被重复选中（依赖读模型最终一致，日级调度足够收敛）。
     * 满期金额由聚合自身保额推导，故本查询无需返回金额。
     * </p>
     *
     * @param policyStatus 保单状态（传 {@code EFFECTIVE}）
     * @param insuranceType 险种（传 {@code ENDOWMENT}）
     * @param endDate 给付基准日（含）
     * @param pageable 分页参数（按主键稳定排序，游标翻页）
     * @return 到期待满期给付的保单列表
     */
    List<PolicyView> findByPolicyStatusAndInsuranceTypeAndEndDateLessThanEqual(PolicyEnum.PolicyStatus policyStatus,
            InsuranceProductType insuranceType, LocalDateTime endDate, Pageable pageable);

    /**
     * 按租户ID统计保单总数（多租户隔离）
     *
     * @param tenantId 租户ID
     * @return 保单总数
     */
    long countByTenantId(String tenantId);

    /**
     * 按状态 + 租户ID统计保单数（有效保单数用 EFFECTIVE）
     *
     * @param policyStatus 保单状态
     * @param tenantId 租户ID
     * @return 命中状态的保单数
     */
    long countByPolicyStatusAndTenantId(PolicyEnum.PolicyStatus policyStatus, String tenantId);

    /**
     * 按创建时间区间 + 租户ID统计保单数（今日新增用当天 00:00~次日 00:00 半开区间）
     *
     * @param tenantId 租户ID
     * @param start 起始时间（含）
     * @param end 结束时间（不含）
     * @return 区间内新增保单数
     */
    long countByTenantIdAndCreateTimeGreaterThanEqualAndCreateTimeLessThan(String tenantId, LocalDateTime start,
            LocalDateTime end);

    /**
     * 按险种三级分类分组统计保单数（管理后台看板分布用）
     * <p>
     * 只统计险种非空的保单，返回 {@code [InsuranceProductType, count]} 行；一级分类归并在应用层完成，
     * 保持仓储职责单一。强制携带 {@code tenantId} 保证多租户隔离。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 每行为 {@code Object[]{InsuranceProductType, Long}} 的分组统计列表
     */
    @Query("SELECT p.insuranceType, COUNT(p) FROM PolicyView p "
            + "WHERE p.tenantId = :tenantId AND p.insuranceType IS NOT NULL GROUP BY p.insuranceType")
    List<Object[]> countGroupByInsuranceType(@Param("tenantId") String tenantId);
}
