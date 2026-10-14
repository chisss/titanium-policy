package com.titanium.policy.infrastructure.messaging;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSONObject;

import com.titanium.common.context.RequestContextHolder;
import com.titanium.common.messaging.Idempotent;
import com.titanium.metadata.topic.CrossDomainTopics;
import com.titanium.policy.application.orchestration.maintenance.context.MaintenanceWriteBackContext;
import com.titanium.policy.application.orchestration.maintenance.strategy.MaintenanceWriteBackStrategy;
import com.titanium.policy.common.enums.PolicyDataUpdateType;
import com.titanium.policy.infrastructure.messaging.inbound.MaintenanceExecutedMessage;

import lombok.extern.slf4j.Slf4j;

/**
 * 保全执行事件监听器（policy 域 Kafka 入站适配器 / 防腐层）
 * <p>
 * 监听 maintenance 域发布的"保全执行完成"事件，解析为防腐上下文后委托应用层策略下发保单命令，
 * 完成"保全审批执行 → 保单状态变更/批改"的回写闭环。policy 是变更的执行者，maintenance 是发起者。
 * </p>
 * <p>
 * <b>归属 infrastructure（driving adapter）</b>：{@code @KafkaListener} 是基础设施入站适配器，
 * 消费外部消息后调用应用层用例/策略（{@link MaintenanceWriteBackStrategy}）。依赖方向 infra→application
 * 符合洋葱架构（外层依赖内层）；发命令的编排逻辑在 application 层的策略实现里，本适配器只做消息接入与防腐翻译。
 * </p>
 * <p>
 * <b>防腐设计</b>：以原始 JSON 解析，<b>不依赖 maintenance 域任何类</b>，避免跨域耦合；保全类型编码
 * 作为两域约定的契约字符串。
 * </p>
 */
@Slf4j
@Component
public class MaintenanceExecutedEventListener {

    /** 保全执行事件主题（与 maintenance 域 MaintenanceConstants.KafkaTopic.MAINTENANCE_EXECUTED 约定一致） */
    private static final String MAINTENANCE_EXECUTED_TOPIC = CrossDomainTopics.MAINTENANCE_EXECUTED;

    /** 数据/要素类批改统一策略的注册键 */
    private static final String ENDORSEMENT_STRATEGY_KEY = "ENDORSEMENT";

    /** 保全类型编码 → 回写策略（应用层编排策略） */
    private final Map<String, MaintenanceWriteBackStrategy> strategyRegistry;

    public MaintenanceExecutedEventListener(List<MaintenanceWriteBackStrategy> strategies) {
        this.strategyRegistry = strategies.stream()
                .collect(Collectors.toMap(MaintenanceWriteBackStrategy::supportedType, Function.identity()));
        log.info("保全回写策略已注册, 支持类型={}", strategyRegistry.keySet());
    }

    /**
     * 消费保全执行事件，翻译为防腐上下文后委托应用层策略下发保单命令
     *
     * @param payload 事件 JSON 报文
     */
    @Idempotent(strategy = Idempotent.Strategy.AGGREGATE_GUARD,
            reason = "重复投递的保全回写命令由聚合幂等吸收（状态/要素不再变更时不落重复事件）；异常一律重抛，由 Kafka 有界重试+死信主题兜底（G16/AC-03）")
    @KafkaListener(topics = MAINTENANCE_EXECUTED_TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void onMaintenanceExecuted(String payload) {
        // 一次性反序列化为防腐入站消息（不依赖 maintenance 域类型），取代手工逐字段解析
        MaintenanceExecutedMessage message = JSONObject.parseObject(payload, MaintenanceExecutedMessage.class);

        if (message.policyId() == null || message.maintenanceType() == null) {
            log.warn("保全执行事件缺少 policyId/maintenanceType，跳过回写, payload={}", payload);
            return;
        }
        if (message.tenantId() == null || message.tenantId().isBlank()) {
            log.error("保全执行事件缺少租户，跳过回写, policyId={}", message.policyId());
            return;
        }

        MaintenanceWriteBackContext context = new MaintenanceWriteBackContext(message.policyId(), message.updatedBy(),
                message.executionDetails(), message.tenantId(), message.maintenanceType(), message.effectiveTime(),
                message.maintenanceIdValue());

        // 状态类保全：精确类型策略（Suspend/Resume/Terminate/Reinstate）
        MaintenanceWriteBackStrategy matchedStrategy = strategyRegistry.get(message.maintenanceType());
        // 数据/要素类批改：回退到统一批改策略
        if (matchedStrategy == null && PolicyDataUpdateType.byMaintenanceType(message.maintenanceType()) != null) {
            matchedStrategy = strategyRegistry.get(ENDORSEMENT_STRATEGY_KEY);
        }
        if (matchedStrategy == null) {
            log.info("保全类型 {} 无匹配回写策略，跳过, policyId={}", message.maintenanceType(), message.policyId());
            return;
        }
        // lambda 捕获要求 effectively final，此处起别名供作用域内使用（原变量在回退分支被重新赋值）
        MaintenanceWriteBackStrategy strategy = matchedStrategy;

        try {
            // 本方法运行在 Kafka 监听线程（无 Web 请求上下文）：先按消息载荷建立身份作用域，使回写
            // 策略派发的保单命令及其下游 Axon 事件线程继承该身份（出站 Feign 路径均 /api/v1/**
            // 强制准入，缺租户头即 400）；作用域退出即还原，不污染线程池复用线程。
            RequestContextHolder.runAsTenant(context.tenantId(), () -> strategy.writeBack(context));
            log.info("保全回写完成, type={}, policyId={}", message.maintenanceType(), message.policyId());
        } catch (Exception e) {
            // 重抛：保单状态已达目标态（重复投递）等失败交 Kafka 有界重试 → 死信主题兜底，不静默吞掉（G16/AC-03）
            log.error("保全回写失败, type={}, policyId={}, 原因={}", message.maintenanceType(), message.policyId(),
                    e.getMessage(), e);
            throw e;
        }
    }
}
