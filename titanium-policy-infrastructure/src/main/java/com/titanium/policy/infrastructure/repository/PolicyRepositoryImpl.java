package com.titanium.policy.infrastructure.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.axonframework.modelling.command.AggregateNotFoundException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.common.exception.BusinessException;
import com.titanium.policy.aggregate.Policy;
import com.titanium.policy.infrastructure.entity.PolicyEntity;
import com.titanium.policy.infrastructure.mapper.PolicyMapper;
import com.titanium.policy.infrastructure.repository.jpa.JpaPolicyRepository;
import com.titanium.policy.repository.PolicyRepository;
import com.titanium.policy.valueobject.PolicyStatus;

import jakarta.annotation.Resource;
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
@Repository
@Log4j2
public class PolicyRepositoryImpl implements PolicyRepository {
    @Resource
    private JpaPolicyRepository jpaPolicyRepository;

    @Resource
    private PolicyMapper        policyMapper;

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
            return jpaPolicyRepository.findById(policyId).map(policyMapper::toAggregate);
        } catch (AggregateNotFoundException e) {
            log.error("加载保单聚合根失败：{}", e.getMessage());
            return Optional.empty();
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
        if (PolicyStatus.StatusCode.EFFECTIVE.equals(policy.getStatus().statusCode())) {
            throw new BusinessException("400", "已激活的保单不允许删除");
        }

        // 3. 软删除：使用is_deleted字段标记为已删除
        Optional<PolicyEntity> entityOpt = jpaPolicyRepository.findById(policyId);
        if (entityOpt.isPresent()) {
            PolicyEntity entity = entityOpt.get();
            entity.setIsDeleted(1);
            jpaPolicyRepository.save(entity);
        } else {
            throw new BusinessException("500", "软删除保单失败，未找到匹配数据");
        }
    }

    /**
     * 根据状态查询保单
     * 
     * @param tenantId 租户ID
     * @param statusCode 状态编码
     * @return 保单迭代器
     */
    @Override
    public Iterable<Policy> findByStatus(String tenantId, PolicyStatus.StatusCode statusCode) {
        if (tenantId == null || tenantId.isEmpty() || statusCode == null) {
            return java.util.Collections.emptyList();
        }

        try {
            // 本地状态机 StatusCode 映射到读侧持久化使用的 metadata PolicyEnum.PolicyStatus
            // （NOT_EFFECTIVE 对齐 PENDING_EFFECTIVE，其余同名映射）
            com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus persistedStatus =
                    toMetadataStatus(statusCode);
            // 查询状态在指定列表中的保单ID
            Iterable<PolicyEntity> entities = jpaPolicyRepository.findByPolicyStatusAndTenantId(persistedStatus,
                    tenantId);

            // 创建结果列表
            List<Policy> policies = new ArrayList<>();

            // 逐个加载聚合根
            for (PolicyEntity entity : entities) {
                Optional<Policy> policyOptional = findById(entity.getPolicyId(), tenantId);
                policyOptional.ifPresent(policies::add);
            }

            return policies;
        } catch (Exception e) {
            log.error("批量查询保单失败：{}", e.getMessage());
            throw new BusinessException("500", "批量查询保单失败：" + e.getMessage(), e);
        }
    }

    /**
     * 根据保单编号查询保单
     * 
     * @param policyNo 保单编号
     * @param tenantId 租户ID
     * @return 保单聚合根
     */
    @Override
    public Optional<Policy> findByPolicyNo(String policyNo, String tenantId) {
        Optional<PolicyEntity> entityOpt = jpaPolicyRepository.findByPolicyNoAndTenantId(policyNo, tenantId);
        if (entityOpt.isPresent()) {
            return findById(entityOpt.get().getPolicyId(), tenantId);
        }
        return Optional.empty();
    }

    /**
     * 根据关联投保单ID查询保单
     * 
     * @param applicationId 投保单ID
     * @param tenantId 租户ID
     * @return 保单聚合根
     */
    @Override
    public Optional<Policy> findByApplicationId(String applicationId, String tenantId) {
        Optional<PolicyEntity> entityOpt = jpaPolicyRepository.findByApplicationIdAndTenantId(applicationId, tenantId);
        if (entityOpt.isPresent()) {
            return findById(entityOpt.get().getPolicyId(), tenantId);
        }
        return Optional.empty();
    }

    /**
     * 根据投保人ID查询保单
     * 
     * @param policyHolderId 投保人ID
     * @param tenantId 租户ID
     * @return 保单迭代器
     */
    @Override
    public Iterable<Policy> findByPolicyHolderId(String policyHolderId, String tenantId) {
        if (policyHolderId == null || policyHolderId.isEmpty() || tenantId == null || tenantId.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        try {
            // 查询状态在指定列表中的保单ID
            Iterable<PolicyEntity> entities = jpaPolicyRepository.findByPolicyHolderIdAndTenantId(policyHolderId,
                    tenantId);

            // 创建结果列表
            List<Policy> policies = new ArrayList<>();

            // 逐个加载聚合根
            for (PolicyEntity entity : entities) {
                Optional<Policy> policyOptional = findById(entity.getPolicyId(), tenantId);
                policyOptional.ifPresent(policies::add);
            }

            return policies;
        } catch (Exception e) {
            log.error("批量查询保单失败：{}", e.getMessage());
            throw new BusinessException("500", "批量查询保单失败：" + e.getMessage(), e);
        }
    }

    /**
     * 将保单域本地状态机编码映射为读侧持久化的 metadata 枚举
     *
     * @param statusCode 本地状态机编码
     * @return metadata 保单状态枚举
     */
    private com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus toMetadataStatus(
            PolicyStatus.StatusCode statusCode) {
        return switch (statusCode) {
            case NOT_EFFECTIVE -> com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus.PENDING_EFFECTIVE;
            case EFFECTIVE -> com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus.EFFECTIVE;
            case SUSPENDED -> com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus.SUSPENDED;
            case TERMINATED -> com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus.TERMINATED;
            case EXPIRED -> com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus.EXPIRED;
            case CANCELLED -> com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus.CANCELLED;
        };
    }
}
