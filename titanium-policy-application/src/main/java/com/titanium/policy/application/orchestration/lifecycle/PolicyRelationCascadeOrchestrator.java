package com.titanium.policy.application.orchestration.lifecycle;

import java.util.List;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.titanium.metadata.enums.policy.PolicyEnum;
import com.titanium.policy.command.SuspendPolicyCommand;
import com.titanium.policy.command.TerminatePolicyCommand;
import com.titanium.policy.event.PolicySuspendedEvent;
import com.titanium.policy.event.PolicyTerminatedEvent;
import com.titanium.policy.query.repository.PolicyRelationViewRepository;
import com.titanium.policy.query.view.PolicyRelationView;

/**
 * 保单父子关系级联编排器（application 事件驱动编排）
 * <p>
 * 团单「父保单状态变更 → 子保单级联」是跨聚合编排（聚合不可变更兄弟聚合），按分层规约属 application 职责，
 * 从 query 读侧迁入本层。以 {@link EventHandler} 监听父保单领域事件，据父子关系读模型对每个子保单下发命令。
 * </p>
 * <p>
 * <b>级联规则</b>：父保单终止 → 全部子保单终止；父保单暂停 → 全部子保单暂停。 子保单各自经其聚合状态机校验，
 * 单个子单非法流转（如已终态）不阻塞其余子单。
 * </p>
 * <p>
 * <b>处理组</b>：独立 {@code policy-cascade-group}，与读侧投影组隔离——编排（发命令）与投影（写读模型）互不干扰。
 * 构造器注入 {@link CommandGateway}（编排发命令，读侧禁持有）与关系读模型仓储（查子保单）。
 * </p>
 */
@Component
@ProcessingGroup("policy-cascade-group")
public class PolicyRelationCascadeOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(PolicyRelationCascadeOrchestrator.class);

    private final CommandGateway               commandGateway;
    private final PolicyRelationViewRepository relationRepository;

    /**
     * 构造器注入（禁用字段注入）
     *
     * @param commandGateway Axon 命令网关
     * @param relationRepository 父子关系读模型仓储
     */
    public PolicyRelationCascadeOrchestrator(CommandGateway commandGateway,
                                             PolicyRelationViewRepository relationRepository) {
        this.commandGateway = commandGateway;
        this.relationRepository = relationRepository;
    }

    /**
     * 父保单终止 → 级联终止全部子保单
     */
    @EventHandler
    public void on(PolicyTerminatedEvent event) {
        List<PolicyRelationView> children = relationRepository.findByParentPolicyIdAndTenantId(event.policyId(),
                event.tenantId());
        if (children.isEmpty()) {
            return;
        }
        LOG.info("[父子级联] 父保单终止, 级联终止 {} 个子保单, parent={}", children.size(), event.policyId());
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
        List<PolicyRelationView> children = relationRepository.findByParentPolicyIdAndTenantId(event.policyId(),
                event.tenantId());
        if (children.isEmpty()) {
            return;
        }
        LOG.info("[父子级联] 父保单暂停, 级联暂停 {} 个子保单, parent={}", children.size(), event.policyId());
        for (PolicyRelationView child : children) {
            sendQuietly(() -> commandGateway.sendAndWait(new SuspendPolicyCommand(child.getChildPolicyId(),
                    "父保单暂停级联", "POLICY_SYSTEM", event.tenantId())), child.getChildPolicyId(), "暂停");
        }
    }

    /**
     * 子保单级联命令容错下发：单个子单非法流转（如已终态）不阻塞其余子单
     */
    private void sendQuietly(Runnable command, String childPolicyId, String action) {
        try {
            command.run();
        } catch (Exception e) {
            LOG.warn("[父子级联] 子保单{}失败, child={}, 原因={}", action, childPolicyId, e.getMessage());
        }
    }
}
