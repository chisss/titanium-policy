package com.titanium.policy.query.handler;

import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.metadata.enums.policy.PolicyEnum;
import com.titanium.policy.command.SuspendPolicyCommand;
import com.titanium.policy.command.TerminatePolicyCommand;
import com.titanium.policy.event.PolicySuspendedEvent;
import com.titanium.policy.event.PolicyTerminatedEvent;
import com.titanium.policy.event.SubPolicyLinkedEvent;
import com.titanium.policy.query.entity.PolicyRelationView;
import com.titanium.policy.query.repository.PolicyRelationViewRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单父子关系级联编排器
 * <p>
 * 维护父子关系读模型，并实现团单"父保单状态变更 → 子保单级联"——这是跨聚合编排，
 * 聚合内无法完成（聚合不可变更兄弟聚合），故在读侧以事件驱动方式对每个子保单单独下发命令。
 * </p>
 * <p>
 * 级联规则：父保单终止 → 所有子保单终止；父保单暂停 → 所有子保单暂停。
 * 子保单各自经其聚合状态机校验（已是终态等非法流转会被拒绝，记录但不阻塞其余子单）。
 * </p>
 */
@Slf4j
@Component
@ProcessingGroup("policy-query-group")
@RequiredArgsConstructor
public class PolicyRelationCascadeHandler {

    private final PolicyRelationViewRepository relationRepository;
    private final CommandGateway               commandGateway;

    /**
     * 投影子保单挂载事件到父子关系读模型
     */
    @EventHandler
    @Transactional
    public void on(SubPolicyLinkedEvent event) {
        log.info("[父子关系投影] 挂载子保单: parent={}, child={}", event.parentPolicyId(), event.childPolicyId());
        PolicyRelationView view = relationRepository.findById(event.childPolicyId()).orElseGet(PolicyRelationView::new);
        view.setChildPolicyId(event.childPolicyId());
        view.setParentPolicyId(event.parentPolicyId());
        view.setGroupId(event.groupId());
        view.setTenantId(event.tenantId());
        view.setLinkedAt(event.linkedAt() != null ? event.linkedAt() : LocalDateTime.now());
        relationRepository.save(view);
    }

    /**
     * 父保单终止 → 级联终止全部子保单
     */
    @EventHandler
    public void on(PolicyTerminatedEvent event) {
        List<PolicyRelationView> children = relationRepository
                .findByParentPolicyIdAndTenantId(event.policyId(), event.tenantId());
        if (children.isEmpty()) {
            return;
        }
        log.info("[父子级联] 父保单终止, 级联终止 {} 个子保单, parent={}", children.size(), event.policyId());
        for (PolicyRelationView child : children) {
            sendQuietly(() -> commandGateway.sendAndWait(new TerminatePolicyCommand(child.getChildPolicyId(),
                    "父保单终止级联", event.operatorId(), PolicyEnum.TerminationReason.CONTRACT_TERMINATION,
                    event.tenantId())), child.getChildPolicyId(), "终止");
        }
    }

    /**
     * 父保单暂停 → 级联暂停全部子保单
     */
    @EventHandler
    public void on(PolicySuspendedEvent event) {
        List<PolicyRelationView> children = relationRepository
                .findByParentPolicyIdAndTenantId(event.policyId(), event.tenantId());
        if (children.isEmpty()) {
            return;
        }
        log.info("[父子级联] 父保单暂停, 级联暂停 {} 个子保单, parent={}", children.size(), event.policyId());
        for (PolicyRelationView child : children) {
            sendQuietly(() -> commandGateway.sendAndWait(new SuspendPolicyCommand(child.getChildPolicyId(),
                    "父保单暂停级联", "POLICY_SYSTEM", event.tenantId())),
                    child.getChildPolicyId(), "暂停");
        }
    }

    /**
     * 子保单级联命令容错下发：单个子单非法流转（如已终态）不阻塞其余子单
     */
    private void sendQuietly(Runnable command, String childPolicyId, String action) {
        try {
            command.run();
        } catch (Exception e) {
            log.warn("[父子级联] 子保单{}失败, child={}, 原因={}", action, childPolicyId, e.getMessage());
        }
    }
}
