package com.titanium.policy.application.orchestration.lifecycle;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Component;

import com.titanium.metadata.enums.policy.PolicyEnum;
import com.titanium.policy.command.TerminatePolicyCommand;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 给付结算终止保单编排器（application 层，跨域「理赔给付结算 → 保单终止」的命令编排）
 * <p>
 * 理赔域完成身故给付/全残给付（{@code DeathBenefitSettledEvent}/{@code DisabilityBenefitSettledEvent}）后，
 * 被保险人身故/全残、保单责任终结（CLAIM-6），据此派发 {@link TerminatePolicyCommand}
 * （终止原因 {@code FULL_PAYMENT} 赔付后终止）终止保单。
 * 由 policy 域 Kafka 防腐监听器解析跨域事件后调用，发命令的编排职责归 application 层
 * （infrastructure 监听器只做消息接入与防腐翻译，不直接发命令）。
 * </p>
 * <p>
 * 命名用 {@code Orchestrator}：本类是跨聚合/跨域的命令编排者，按规约不用与 Axon 消息处理器
 * 撞名的 Handler/Processor 后缀。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeathBenefitTerminationOrchestrator {

    private final CommandGateway commandGateway;

    /**
     * 给付结算完成 → 终止保单（赔付后终止）。
     * <p>
     * 身故给付与全残给付共用：被保险人身故/全残后保单责任一次性终结（CLAIM-6），
     * 终止原因统一 {@code FULL_PAYMENT}。终止描述文案由调用方（入站监听器）以常量传入，
     * 避免编排器感知场景差异（红线 20 常量化）。
     * </p>
     * <p>
     * 幂等保护：保单已处于终态时 {@code Policy.handle(TerminatePolicyCommand)} 会抛业务异常，
     * 由调用方（监听器）捕获记录而不阻塞消费，容忍重复投递。
     * </p>
     *
     * @param policyId 保单ID
     * @param operatorId 操作人（理赔结算人/系统）
     * @param tenantId 租户ID
     * @param terminationDescription 终止描述文案（调用方常量，落库）
     */
    public void terminateOnBenefitSettled(String policyId, String operatorId, String tenantId,
                                          String terminationDescription) {
        log.info("[给付结算-终止保单] 派发终止命令, policyId={}", policyId);
        commandGateway.sendAndWait(new TerminatePolicyCommand(policyId, terminationDescription, operatorId,
                PolicyEnum.TerminationReason.FULL_PAYMENT, tenantId));
    }
}
