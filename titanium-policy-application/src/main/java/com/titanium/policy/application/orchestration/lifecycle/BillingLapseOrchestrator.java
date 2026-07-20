package com.titanium.policy.application.orchestration.lifecycle;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Component;

import com.titanium.policy.command.LapsePolicyCommand;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 计费失效保单编排器（application 层，跨域「计费宽限期满 → 保单失效」的命令编排）
 * <p>
 * 计费域检测到"保费收取失败、宽限期满"后发布失效通知，据此派发 {@link LapsePolicyCommand} 使保单进入
 * 失效(LAPSED)状态：保障暂停但可经复效恢复。billing 域是失效检测者，policy 域是保单状态执行者。
 * 由 policy 域 Kafka 防腐监听器解析跨域通知后调用，发命令的编排职责归 application 层
 * （infrastructure 监听器只做消息接入与防腐翻译，不直接持有 CommandGateway 发命令）。
 * </p>
 * <p>
 * 命名用 {@code Orchestrator}：本类是跨域命令编排者，按规约不用与 Axon 消息处理器撞名的 Handler/Processor 后缀。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillingLapseOrchestrator {

    /** 系统失效操作者标识（billing 域触发的自动失效） */
    private static final String SYSTEM_LAPSE_OPERATOR = "SYSTEM_AUTO_LAPSE";

    private final CommandGateway commandGateway;

    /**
     * 计费失效通知 → 保单失效。
     * <p>
     * 幂等保护：保单已处于失效/终态时 {@code Policy.handle(LapsePolicyCommand)} 会抛业务异常，
     * 由调用方（监听器）捕获记录而不阻塞消费，容忍重复投递。
     * </p>
     *
     * @param policyId 保单ID
     * @param reason   失效原因（为空时用默认"宽限期满未缴保费"）
     * @param tenantId 租户ID
     */
    public void lapseOnBillingNotification(String policyId, String reason, String tenantId) {
        log.info("[计费失效-编排] 派发保单失效命令, policyId={}, reason={}", policyId, reason);
        commandGateway.sendAndWait(new LapsePolicyCommand(
                policyId,
                reason != null ? reason : "宽限期满未缴保费",
                SYSTEM_LAPSE_OPERATOR,
                tenantId));
    }
}
