package com.titanium.policy.infrastructure.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSONObject;

import com.titanium.policy.application.orchestration.lifecycle.BillingLapseOrchestrator;
import com.titanium.policy.infrastructure.messaging.inbound.BillingLapseNotificationMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 计费失效通知监听器（policy 域 Kafka 入站适配器 / 防腐层）
 * <p>
 * 监听 billing 域发布的"保费收取失败导致保单失效"通知，据 policyId 发送保单失效命令，完成
 * 「计费宽限期满 → 保单失效」的跨域闭环。宽限期满仍未缴费，保单进入失效(LAPSED)状态，
 * 保障暂停但可经复效恢复。billing 域是失效检测者，policy 域是保单状态的执行者。
 * </p>
 * <p>
 * <b>归属 infrastructure（driving adapter）</b>：{@code @KafkaListener} 消费外部消息后发送命令，
 * 依赖方向 infra→domain（CommandGateway）符合洋葱架构；命令处理逻辑在 Policy 聚合根，
 * 本适配器只做消息接入与防腐翻译。
 * </p>
 * <p>
 * <b>防腐设计</b>：以原始 JSON 解析，<b>不依赖 billing 域任何类</b>，避免跨域耦合。
 * billing 域仅贯穿 policyId（裸字符串）和 tenantId。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillingLapseNotificationListener {

    /** 计费失效通知主题（与 billing 域 BillingConstants.KafkaTopic.LAPSE_NOTIFICATION 约定一致） */
    private static final String LAPSE_NOTIFICATION_TOPIC = "titanium.billing.lapse-notification";

    /** 失效命令编排器（application 层，发命令职责归此，infra 监听器不直接持有 CommandGateway） */
    private final BillingLapseOrchestrator billingLapseOrchestrator;

    /**
     * 消费计费失效通知，据 policyId 发送保单失效命令。
     *
     * @param payload 事件 JSON 报文
     */
    @KafkaListener(topics = LAPSE_NOTIFICATION_TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void onLapseNotification(String payload) {
        try {
            // 一次性反序列化为防腐入站消息（不依赖 billing 域类型），取代手工逐字段解析
            BillingLapseNotificationMessage message = JSONObject.parseObject(payload,
                    BillingLapseNotificationMessage.class);

            String policyId = message.policyId();
            String reason = message.reason();
            String tenantId = message.tenantId();

            if (policyId == null || policyId.isBlank()) {
                log.warn("[计费失效-入站] 通知缺少 policyId，跳过保单失效, payload={}", payload);
                return;
            }

            log.info("[计费失效-入站] 收到计费失效通知, policyId={}, reason={}, 触发保单失效", policyId, reason);

            // 发命令编排下沉 application 层，infra 监听器只做消息接入与防腐翻译
            billingLapseOrchestrator.lapseOnBillingNotification(policyId, reason, tenantId);

            log.info("[计费失效-入站] 保单失效命令已发送, policyId={}", policyId);

        } catch (Exception e) {
            // 幂等保护：保单已达失效状态（重复投递）或其他业务异常，记录但不阻塞消费
            // 避免 Kafka 无限重试，可选记死信队列（TODO：集成 DLQ）
            log.error("[计费失效-入站] 保单失效处理失败, payload={}, 原因={}", payload, e.getMessage(), e);
        }
    }
}
