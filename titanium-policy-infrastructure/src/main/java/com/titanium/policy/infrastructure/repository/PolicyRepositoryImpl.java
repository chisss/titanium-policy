package com.titanium.policy.infrastructure.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.axonframework.modelling.command.Aggregate;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.axonframework.modelling.command.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.common.exception.BusinessException;
import com.titanium.metadata.enums.PolicyEnum.PolicyStatus;
import com.titanium.policy.aggregate.Policy;
import com.titanium.policy.repository.PolicyRepository;

import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.extern.log4j.Log4j2;

/**
 * 保单仓储实现类（基础设施层）
 * <p>
 * 适配Axon事件溯源框架，实现多租户隔离的保单聚合根仓储操作
 * </p>
 * 
 * @since 1.0.0
 * @note 核心设计： 1. 聚合根加载：通过Axon Repository加载，结合多租户校验； 2.
 *       持久化：Axon事件溯源自动保存事件，save方法仅做参数校验； 3. 删除：采用软删除（标记状态），而非硬删除，符合事件溯源设计；
 */
@org.springframework.stereotype.Repository
@Log4j2
public class PolicyRepositoryImpl implements PolicyRepository {
    @PersistenceContext
    private EntityManager      entityManager;

    @Resource
    private Repository<Policy> axonRepository;

    /**
     * 按ID+租户ID查询保单聚合根
     * <p>
     * 通过Axon Repository加载聚合根，并校验多租户隔离
     * </p>
     * 
     * @param policyId 保单聚合根ID
     * @param tenantId 租户ID（多租户隔离）
     * @return 保单聚合根Optional，不存在则返回empty
     */
    @Override
    public Optional<Policy> findById(String policyId, String tenantId) {
        try {
            // 1. 加载Axon聚合根包装对象（适配你的Aggregate<T>接口）
            Aggregate<Policy> aggregate = axonRepository.load(policyId);

            // 2. 通过invoke方法获取聚合根实例+多租户校验（核心修正）
            Policy policy = aggregate.invoke(aPolicy -> {
                // 这里的policy就是聚合根实例，在invoke内部完成多租户校验
                if (!tenantId.equals(aPolicy.getTenantId())) {
                    throw new BusinessException("403", "无权访问其他租户的保单数据");
                }
                return aPolicy; // 返回聚合根实例
            });

            return Optional.of(policy);
        } catch (AggregateNotFoundException e) {
            // 聚合根不存在，返回空（预期异常）
            return Optional.empty();
        } catch (BusinessException e) {
            log.error("多租户违规：{}", e.getMessage());
            // 业务异常（多租户违规），抛上层处理
            throw e;
        } catch (Exception e) {
            log.error("保存保单聚合根异常：{}", e.getMessage());
            // 非预期异常，包装成业务异常抛出
            throw new BusinessException("500", "加载保单聚合根失败：" + e.getMessage(), e);
        }
    }

    /**
     * 保存保单聚合根（Axon事件溯源模式下，仅做参数校验）
     * <p>
     * Axon中聚合根的持久化由事件溯源自动处理（事件保存到Event Store）， 此处save方法仅校验聚合根合法性，确保多租户隔离
     * </p>
     * 
     * @param policy 保单聚合根
     * @return 校验后的聚合根
     * @throws BusinessException 当聚合根参数不合法时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class) // 仅保存操作加事务
    public Policy save(Policy policy) {
        // 1. 非空校验
        if (policy == null) {
            throw new BusinessException("400", "保单聚合根不能为空");
        }
        // 2. 多租户ID校验
        if (policy.getTenantId() == null || policy.getTenantId().isEmpty()) {
            throw new BusinessException("400", "保单聚合根必须关联租户ID");
        }
        // 3. 核心ID校验
        if (policy.getPolicyId() == null || policy.getPolicyId().isEmpty()) {
            throw new BusinessException("400", "保单聚合根ID不能为空");
        }
        // Axon会在命令处理+事件应用时自动持久化事件，此处无需额外操作
        return policy;
    }

    /**
     * 软删除保单（标记为已删除状态，而非硬删除）
     * <p>
     * 符合事件溯源设计：保留事件和聚合根数据，仅标记状态，可追溯删除操作
     * </p>
     * 
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @throws BusinessException 当保单状态不允许删除/跨租户删除时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(String policyId, String tenantId) {
        // 1. 先查询聚合根，校验状态和租户
        Policy policy = findById(policyId, tenantId).orElseThrow(() -> new BusinessException("404", "保单不存在或无权访问"));

        // 2. 业务规则校验：激活/生效状态的保单不允许删除
        if (PolicyStatus.EFFECTIVE.equals(policy.getStatus())) {
            throw new BusinessException("400", "已激活的保单不允许删除");
        }

        // 3. 软删除：使用is_deleted字段标记为已删除
        String deleteHql = "UPDATE PolicyEntity p SET p.isDeleted = 1, p.updateTime = CURRENT_TIMESTAMP "
                + "WHERE p.id = :policyId AND p.tenantId = :tenantId AND p.isDeleted = 0";
        Query deleteQuery = entityManager.createQuery(deleteHql).setParameter("policyId", policyId)
                .setParameter("tenantId", tenantId);

        int affectedRows = deleteQuery.executeUpdate();
        if (affectedRows == 0) {
            throw new BusinessException("500", "软删除保单失败，未找到匹配数据");
        }
    }

    @Override
    public Iterable<Policy> findByStatusIn(String tenantId, PolicyStatus... statuses) {
        if (tenantId == null || tenantId.isEmpty() || statuses == null || statuses.length == 0) {
            return java.util.Collections.emptyList();
        }

        try {
            // 查询状态在指定列表中的保单ID
            String selectHql = "SELECT p.id FROM PolicyEntity p WHERE p.tenantId = :tenantId AND p.policyStatus IN :statuses AND p.isDeleted = 0";
            Query query = entityManager.createQuery(selectHql).setParameter("tenantId", tenantId)
                    .setParameter("statuses", java.util.Arrays.asList(statuses));

            // 获取所有匹配的保单ID
            List<String> policyIds = query.getResultList();

            // 创建结果列表
            List<Policy> policies = new ArrayList<>();

            // 逐个加载聚合根（注意：对于大量数据，这可能会导致性能问题）
            for (String policyId : policyIds) {
                Optional<Policy> policyOptional = findById(policyId, tenantId);
                policyOptional.ifPresent(policies::add);
            }

            return policies;
        } catch (Exception e) {
            log.error("批量查询保单失败：{}", e.getMessage());
            throw new BusinessException("500", "批量查询保单失败：" + e.getMessage(), e);
        }
    }
}
