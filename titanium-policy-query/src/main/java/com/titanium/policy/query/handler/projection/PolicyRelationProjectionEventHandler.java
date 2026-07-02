package com.titanium.policy.query.handler.projection;

import java.time.LocalDateTime;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.policy.event.SubPolicyLinkedEvent;
import com.titanium.policy.query.repository.PolicyRelationViewRepository;
import com.titanium.policy.query.view.PolicyRelationView;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单父子关系读模型投影处理器（CQRS 读侧）
 * <p>
 * 订阅 {@link SubPolicyLinkedEvent} 将团单父子挂载关系投影到读模型表 {@code t_policy_relation}。
 * 纯读模型投影，<b>不发命令、不做跨聚合编排</b>——父子级联编排已迁出至
 * {@code application/orchestration/PolicyRelationCascadeOrchestrator}（读侧不得持有 CommandGateway）。
 * </p>
 */
@Slf4j
@Component
@ProcessingGroup("policy-query-group")
@RequiredArgsConstructor
public class PolicyRelationProjectionEventHandler {

    private final PolicyRelationViewRepository relationRepository;

    /**
     * 投影子保单挂载事件到父子关系读模型
     */
    @EventHandler
    @Transactional
    public void on(SubPolicyLinkedEvent event) {
        log.info("[父子关系投影] 挂载子保单: parent={}, child={}", event.parentPolicyId(), event.childPolicyId());
        PolicyRelationView view = relationRepository.findById(event.childPolicyId())
                .orElseGet(PolicyRelationView::new);
        view.setChildPolicyId(event.childPolicyId());
        view.setParentPolicyId(event.parentPolicyId());
        view.setGroupId(event.groupId());
        view.setTenantId(event.tenantId());
        view.setLinkedAt(event.linkedAt() != null ? event.linkedAt() : LocalDateTime.now());
        LocalDateTime now = LocalDateTime.now();
        if (view.getCreateTime() == null) {
            view.setCreateTime(now);
        }
        view.setUpdateTime(now);
        relationRepository.save(view);
    }
}
