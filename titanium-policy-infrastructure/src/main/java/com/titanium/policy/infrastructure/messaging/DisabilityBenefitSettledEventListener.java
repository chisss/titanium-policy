package com.titanium.policy.infrastructure.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSONObject;

import com.titanium.policy.application.orchestration.lifecycle.DeathBenefitTerminationOrchestrator;
import com.titanium.policy.infrastructure.messaging.inbound.DisabilityBenefitSettledMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 全残给付结算事件监听器（policy 域 Kafka 入站适配器 / 防腐层）
 * <p>
 * 监听 claim 域发布的"全残给付结算完成"事件，据 policyId 委托应用层编排器终止保单，完成
 * 「理赔全残给付 → 保单责任终结」的跨域闭环（CLAIM-6：全残给付后保单责任终止，同身故）。
 * 与身故给付（{@link DeathBenefitSettledEventListener}）共用同一终止编排器，仅事件主题与
 * 终止文案不同。
 * </p>
 * <p>
 * <b>归属 infrastructure（driving adapter）</b>：{@code @KafkaListener} 消费外部消息后调用应用层
 * 编排器，依赖方向 infra→application 符合洋葱架构；发命令的编排逻辑在 application 层，
 * 本适配器只做消息接入与防腐翻译。
 * </p>
 * <p>
 * <b>防腐设计</b>：以原始 JSON 解析，<b>不依赖 claim 域任何类</b>，避免跨域耦合。
 * claim 域事件 policyId 序列化为裸字符串。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DisabilityBenefitSettledEventListener {

    /** 全残给付结算事件主题（与 claim 域 ClaimConstants.KafkaTopic.DISABILITY_BENEFIT_SETTLED 约定一致） */
    private static final String DISABILITY_BENEFIT_SETTLED_TOPIC = "claim-disability-benefit-settled";

    /** 终止描述文案常量（落库，红线 20 禁裸串） */
    private static final String TERMINATION_DESCRIPTION = "全残给付后保单责任终止";

    private final DeathBenefitTerminationOrchestrator terminationOrchestrator;

    /**
     * 消费全残给付结算事件，据 policyId 终止保单（赔付后终止）。
     *
     * @param payload 事件 JSON 报文
     */
    @KafkaListener(topics = DISABILITY_BENEFIT_SETTLED_TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void onDisabilityBenefitSettled(String payload) {
        // 一次性反序列化为防腐入站消息（不依赖 claim 域类型），取代手工逐字段解析
        DisabilityBenefitSettledMessage message = JSONObject.parseObject(payload, DisabilityBenefitSettledMessage.class);
        String policyId = message.policyId();
        if (policyId == null || policyId.isBlank()) {
            log.warn("[全残给付-入站] 事件缺少 policyId，跳过保单终止, payload={}", payload);
            return;
        }
        // claim 域事件未贯穿 operatorId，以系统账号兜底，保单按 policyId 强一致路由
        String operatorId = "claim-disability-settlement";

        log.info("[全残给付-入站] 收到全残给付结算, policyId={}, 触发保单终止", policyId);
        try {
            terminationOrchestrator.terminateOnBenefitSettled(policyId, operatorId, message.tenantId(),
                    TERMINATION_DESCRIPTION);
        } catch (Exception e) {
            // 幂等保护：保单已达终态（重复投递）会抛业务异常，记录但不阻塞消费
            log.error("[全残给付-入站] 保单终止失败, policyId={}, 原因={}", policyId, e.getMessage());
        }
    }
}
