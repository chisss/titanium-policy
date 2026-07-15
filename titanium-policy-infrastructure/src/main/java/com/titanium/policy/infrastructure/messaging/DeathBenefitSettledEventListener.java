package com.titanium.policy.infrastructure.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSONObject;

import com.titanium.policy.application.orchestration.DeathBenefitTerminationOrchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 身故给付结算事件监听器（policy 域 Kafka 入站适配器 / 防腐层）
 * <p>
 * 监听 claim 域发布的"身故给付结算完成"事件，据 policyId 委托应用层编排器终止保单，完成
 * 「理赔身故给付 → 保单责任终结」的跨域闭环。被保险人身故、保单一次性给付后终止，区别于
 * 年金的生存给付（逐期给付不终止）。理赔域是给付发起者，policy 域是保单终止的执行者。
 * </p>
 * <p>
 * <b>归属 infrastructure（driving adapter）</b>：{@code @KafkaListener} 消费外部消息后调用应用层
 * 编排器（{@link DeathBenefitTerminationOrchestrator}），依赖方向 infra→application 符合洋葱架构；
 * 发命令的编排逻辑在 application 层，本适配器只做消息接入与防腐翻译。
 * </p>
 * <p>
 * <b>防腐设计</b>：以原始 JSON 解析，<b>不依赖 claim 域任何类</b>，避免跨域耦合。claim 域 ClaimId
 * 值对象序列化为 {@code {"value":"..."}}，policyId 序列化为裸字符串。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeathBenefitSettledEventListener {

    /** 身故给付结算事件主题（与 claim 域 ClaimConstants.KafkaTopic.DEATH_BENEFIT_SETTLED 约定一致） */
    private static final String DEATH_BENEFIT_SETTLED_TOPIC = "claim-death-benefit-settled";

    private final DeathBenefitTerminationOrchestrator terminationOrchestrator;

    /**
     * 消费身故给付结算事件，据 policyId 终止保单（赔付后终止）。
     *
     * @param payload 事件 JSON 报文
     */
    @KafkaListener(topics = DEATH_BENEFIT_SETTLED_TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void onDeathBenefitSettled(String payload) {
        JSONObject json = JSONObject.parseObject(payload);
        String policyId = json.getString("policyId");
        if (policyId == null || policyId.isBlank()) {
            log.warn("[身故给付-入站] 事件缺少 policyId，跳过保单终止, payload={}", payload);
            return;
        }
        // claim 域事件未贯穿 tenantId/operatorId，以系统账号兜底，保单按 policyId 强一致路由
        String operatorId = "claim-death-settlement";
        String tenantId = json.getString("tenantId");

        log.info("[身故给付-入站] 收到身故给付结算, policyId={}, 触发保单终止", policyId);
        try {
            terminationOrchestrator.terminateOnDeathBenefit(policyId, operatorId, tenantId);
        } catch (Exception e) {
            // 幂等保护：保单已达终态（重复投递）会抛业务异常，记录但不阻塞消费
            log.error("[身故给付-入站] 保单终止失败, policyId={}, 原因={}", policyId, e.getMessage());
        }
    }
}
