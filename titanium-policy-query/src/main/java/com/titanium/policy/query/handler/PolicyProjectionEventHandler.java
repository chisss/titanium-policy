package com.titanium.policy.query.handler;

import java.time.LocalDateTime;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.metadata.enums.policy.PolicyEnum;
import com.titanium.policy.event.PolicyActivatedEvent;
import com.titanium.policy.event.PolicyCancelledEvent;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.event.PolicyExpiredEvent;
import com.titanium.policy.event.PolicyIssuedEvent;
import com.titanium.policy.event.PolicyLapsedEvent;
import com.titanium.policy.event.PolicyReinstatedEvent;
import com.titanium.policy.event.PolicyResumedEvent;
import com.titanium.policy.event.PolicySuspendedEvent;
import com.titanium.policy.event.PolicyTerminatedEvent;
import com.titanium.policy.query.entity.PolicyView;
import com.titanium.policy.query.repository.PolicyViewRepository;
import com.titanium.policy.valueobject.PolicyStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单读模型投影事件处理器（CQRS 读侧核心）
 * <p>
 * 订阅保单域领域事件，将聚合根状态变更投影到读模型表 {@code t_policy_view}， 补齐此前缺失的 CQRS
 * 读模型投影机制，实现真正的读写分离。
 * </p>
 * <p>
 * <b>处理组</b>：{@code policy-query-group}，与现有
 * {@link PolicyQueryHandler}（QueryHandler）共用同一处理组， 复用 bootstrap 中已配置的 DLQ
 * 死信队列与 tracking 模式。
 * </p>
 * <p>
 * <b>幂等性</b>：创建事件用 saveOrUpdate 语义；状态更新事件先查存量再更新，缺失时告警并跳过， 保证事件重放（Replay）时不产生脏数据。
 * </p>
 */
@Slf4j
@Component
@ProcessingGroup("policy-query-group")
@RequiredArgsConstructor
public class PolicyProjectionEventHandler {

    private final PolicyViewRepository policyViewRepository;

    /**
     * 投影保单创建事件：新建读模型记录
     */
    @EventHandler
    @Transactional
    public void on(PolicyCreatedEvent event) {
        log.info("[读模型投影] 保单创建: policyId={}, tenantId={}", event.policyId(), event.tenantId());

        PolicyView view = policyViewRepository.findByPolicyIdAndTenantId(event.policyId(), event.tenantId())
                .orElseGet(PolicyView::new);

        LocalDateTime now = LocalDateTime.now();
        view.setPolicyId(event.policyId());
        view.setPolicyNo(event.policyNo() != null ? event.policyNo().value() : null);
        view.setPolicyStatus(event.status() != null ? mapStatus(event.status().statusCode())
                : PolicyEnum.PolicyStatus.PENDING_EFFECTIVE);
        if (event.premium() != null) {
            view.setPremium(event.premium().value());
            view.setCurrency(CurrencyEnum.fromCode(event.premium().currency()));
        }
        view.setStartDate(event.effectiveDate());
        view.setEndDate(event.expiryDate());
        view.setTenantId(event.tenantId());
        if (view.getCreateTime() == null) {
            view.setCreateTime(now);
        }
        view.setUpdateTime(now);

        policyViewRepository.save(view);
    }

    /**
     * 投影保单签发事件：记录签发时间
     */
    @EventHandler
    @Transactional
    public void on(PolicyIssuedEvent event) {
        applyUpdate(event.policyId(), event.tenantId(), "保单签发", view -> view.setIssueTime(event.issueTime()));
    }

    /**
     * 投影保单生效事件
     */
    @EventHandler
    @Transactional
    public void on(PolicyActivatedEvent event) {
        applyStatus(event.policyId(), event.tenantId(), PolicyStatus.StatusCode.EFFECTIVE);
    }

    /**
     * 投影保单暂停事件
     */
    @EventHandler
    @Transactional
    public void on(PolicySuspendedEvent event) {
        applyStatus(event.policyId(), event.tenantId(), PolicyStatus.StatusCode.SUSPENDED);
    }

    /**
     * 投影保单恢复事件
     */
    @EventHandler
    @Transactional
    public void on(PolicyResumedEvent event) {
        applyStatus(event.policyId(), event.tenantId(), PolicyStatus.StatusCode.EFFECTIVE);
    }

    /**
     * 投影保单终止事件
     */
    @EventHandler
    @Transactional
    public void on(PolicyTerminatedEvent event) {
        applyStatus(event.policyId(), event.tenantId(), PolicyStatus.StatusCode.TERMINATED);
    }

    /**
     * 投影保单到期事件
     */
    @EventHandler
    @Transactional
    public void on(PolicyExpiredEvent event) {
        applyStatus(event.policyId(), event.tenantId(), PolicyStatus.StatusCode.EXPIRED);
    }

    /**
     * 投影保单失效事件（宽限期满未缴费）
     */
    @EventHandler
    @Transactional
    public void on(PolicyLapsedEvent event) {
        applyStatus(event.policyId(), event.tenantId(), PolicyStatus.StatusCode.LAPSED);
    }

    /**
     * 投影保单复效事件（补缴+核保通过后恢复生效）
     */
    @EventHandler
    @Transactional
    public void on(PolicyReinstatedEvent event) {
        applyStatus(event.policyId(), event.tenantId(), PolicyStatus.StatusCode.EFFECTIVE);
    }

    /**
     * 投影保单取消事件
     */
    @EventHandler
    @Transactional
    public void on(PolicyCancelledEvent event) {
        applyStatus(event.policyId(), event.tenantId(), PolicyStatus.StatusCode.CANCELLED);
    }

    /**
     * 通用状态投影：更新读模型状态字段
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @param newStatus 目标状态
     */
    private void applyStatus(String policyId, String tenantId, PolicyStatus.StatusCode newStatus) {
        log.info("[读模型投影] 状态变更: policyId={}, status={}", policyId, newStatus.name());
        applyUpdate(policyId, tenantId, "状态变更-" + newStatus.name(),
                view -> view.setPolicyStatus(mapStatus(newStatus)));
    }

    /**
     * 本地状态机编码 → metadata 读模型保单状态枚举
     * <p>
     * NOT_EFFECTIVE 对齐 metadata 的 PENDING_EFFECTIVE，其余同名映射。
     * </p>
     *
     * @param statusCode 本地状态机编码
     * @return metadata 保单状态枚举
     */
    private PolicyEnum.PolicyStatus mapStatus(PolicyStatus.StatusCode statusCode) {
        return switch (statusCode) {
            case NOT_EFFECTIVE -> PolicyEnum.PolicyStatus.PENDING_EFFECTIVE;
            case EFFECTIVE -> PolicyEnum.PolicyStatus.EFFECTIVE;
            case SUSPENDED -> PolicyEnum.PolicyStatus.SUSPENDED;
            case TERMINATED -> PolicyEnum.PolicyStatus.TERMINATED;
            case EXPIRED -> PolicyEnum.PolicyStatus.EXPIRED;
            case LAPSED -> PolicyEnum.PolicyStatus.LAPSED;
            case CANCELLED -> PolicyEnum.PolicyStatus.CANCELLED;
        };
    }

    /**
     * 通用更新模板：查存量→应用变更→刷新更新时间→保存；缺失时告警跳过保证幂等
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @param action 操作描述（日志用）
     * @param mutator 字段变更逻辑
     */
    private void applyUpdate(String policyId, String tenantId, String action,
                             java.util.function.Consumer<PolicyView> mutator) {
        policyViewRepository.findByPolicyIdAndTenantId(policyId, tenantId).ifPresentOrElse(view -> {
            mutator.accept(view);
            view.setUpdateTime(LocalDateTime.now());
            policyViewRepository.save(view);
        }, () -> log.warn("[读模型投影] {} 失败：未找到读模型记录 policyId={}, tenantId={}（可能事件乱序，将由DLQ重试）", action,
                policyId, tenantId));
    }
}
