package com.titanium.policy.application.orchestration.payment;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Component;

import com.titanium.policy.command.ActivatePolicyCommand;
import com.titanium.policy.command.RecordPremiumCollectionCommand;
import com.titanium.policy.common.constant.PolicyConstants;
import com.titanium.policy.valueobject.payment.PremiumCollectionNotice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保费实收回写编排器（application/orchestration/payment，对端域 payment）
 * <p>
 * 「支付域出账成功 → 保单实收 + 生效」的命令式编排：出单收费编排建单后保单停在未生效态，
 * 待支付回调满足 {@code Policy.canActivate()} 的保费条件。本类是该回调在应用层的落点——
 * 消费侧防腐监听器（infrastructure）解析支付消息后以 {@link PremiumCollectionNotice} 委托本编排器，
 * 本类派发 {@link RecordPremiumCollectionCommand} 写回实收、再派发 {@link ActivatePolicyCommand} 驱动生效。
 * </p>
 * <p>
 * <b>两步两事务、可残缺续跑</b>：实收回写与保单生效是两条命令、两个事务。生效失败（保障起期未到）
 * 不回滚实收——实收是已发生的资金事实，生效由 {@code PolicyActivationScheduler}（application/scheduled，
 * 按「待生效 + 起期已到」跨租户扫描）补齐。反之先激活则会因保费条件不满足被聚合拒绝，故顺序不可颠倒。
 * </p>
 * <p>
 * 命名用 {@code Orchestrator}：本类是跨域命令编排者，按规约不用与 Axon 消息处理器撞名的
 * Handler/Processor 后缀。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PremiumCollectionResultOrchestrator {

    private final CommandGateway commandGateway;

    /**
     * 支付成功 → 保单实收回写并尝试生效。
     * <p>
     * 幂等：{@code Policy.handle(RecordPremiumCollectionCommand)} 按 paymentId 去重，
     * 重复投递抛 {@code POLICY_PAYMENT_DUPLICATE}，由调用方（监听器）捕获记录而不阻塞消费。
     * </p>
     *
     * @param notice 保费实收回写通知（支付域支付成功事实的领域表达）
     */
    public void onPaymentSucceeded(PremiumCollectionNotice notice) {
        recordCollection(notice);
        activateIfReady(notice);
    }

    /**
     * 回写实收事实。
     * <p>
     * {@code paymentMethod} 传 null：该字段类型是缴费频率（年缴/月缴/趸缴…），属保单属性而非支付结果，
     * 跨域支付载荷不携带；下游读侧投影亦不消费该字段，故不臆造。缴费频率已在出单时经
     * {@code PremiumPlan} 落定。
     * </p>
     */
    private void recordCollection(PremiumCollectionNotice notice) {
        commandGateway.sendAndWait(new RecordPremiumCollectionCommand(
                notice.policyId(), notice.paymentId(), notice.paymentNo(), notice.collectedAmount(),
                null, notice.collectedAt(), PolicyConstants.POLICY_SYSTEM, notice.tenantId()));
        log.info("[保费收讫-编排] 实收已回写保单: policyId={}, paymentNo={}, 金额={}", notice.policyId(),
                notice.paymentNo(), notice.collectedAmount());
    }

    /**
     * 收讫后驱动生效；不可生效属正常（保障起期未到 / 已生效），不阻断消费。
     */
    private void activateIfReady(PremiumCollectionNotice notice) {
        try {
            commandGateway.sendAndWait(new ActivatePolicyCommand(notice.policyId(), notice.tenantId()));
            log.info("[保费收讫-编排] 保费条件已满足，保单已生效: policyId={}", notice.policyId());
        } catch (Exception ex) {
            // 非故障：保障起期未到（待定时激活任务）、保单已生效（重复投递）均在此收敛，
            // 重抛只会让同一条消息被无限重放而结果不变。
            log.info("[保费收讫-编排] 保单暂不可生效（保障起期未到或已生效，待定时任务激活）: policyId={}, 原因={}",
                    notice.policyId(), ex.getMessage());
        }
    }
}
