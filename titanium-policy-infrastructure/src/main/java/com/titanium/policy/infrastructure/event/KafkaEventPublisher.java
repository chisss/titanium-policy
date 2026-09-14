package com.titanium.policy.infrastructure.event;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;

import com.titanium.common.kafka.KafkaPublishSupport;
import com.titanium.policy.common.constant.PolicyConstants;
import com.titanium.policy.event.PolicyActivatedEvent;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.event.PolicyIssuedEvent;

import lombok.AllArgsConstructor;

/**
 * Kafka事件发布器，用于将领域事件发布到Kafka。
 *
 * <p><b>处理组形态</b>：{@link #PROCESSING_GROUP} 以 {@code mode: tracking} + {@code dlq.enabled: true}
 * 装配（见 application.yml）。tracking 是死信队列的前提（Axon 的 SequencedDeadLetterQueue 仅支持流式
 * 处理器）；DLQ 则让发布失败的事件可被 {@code DeadLetterQueueService} 定时重投。</p>
 *
 * <p><b>首启位点</b>：出站组登记在 {@code titanium.axon.outbound-relay.groups} 中，初始位点为事件流
 * <b>末端</b>——否则全新部署会把保单域全部历史事件重放给下游。</p>
 */
@Component
@AllArgsConstructor
@ProcessingGroup(KafkaEventPublisher.PROCESSING_GROUP)
public class KafkaEventPublisher {

    /**
     * 跨域出站处理组名。
     * <p>🔴 必须与 application.yml 的 {@code axon.eventhandling.processors.<name>} 键一致：二者漂移时
     * Axon 会为本组退回默认 tracking 且丢失 DLQ 配置，处理器仍存在（故不报错）但失去重投能力。</p>
     */
    public static final String PROCESSING_GROUP = "policy-kafka-group";

    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * 处理保单创建事件。
     * <p>
     * 🔴 本主题**经判定不应接 billing 开单**（2026-09-14 m6-906）：保单创建时点的首期保费账单，已由出单用例
     * <b>同步</b>开立——{@code IssuanceOrchestrator.executeOneStep}/{@code IssuanceSaga} 在创建保单后立即委托
     * {@code PremiumCollectionOrchestrator.collect} → {@code BillingServicePort.createPremiumBill} →
     * billing {@code BillApi}（Feign），且该结果是本用例的<b>必需输入</b>（据此建支付单、标记收讫、决定能否
     * 激活）。Kafka 为异步最终一致，既拿不到账单结果，又会在 billing 侧再开一张应收账单——同一保单出现两张
     * 应收，属重复计费。判定依据与 billing 侧兜底监听器（{@code PolicyActivatedEventListener}）的分工见
     * {@code docs/技术文档/跨域事件目录-2026-09.md} §六.14。
     * </p>
     */
    @EventHandler
    public void handlePolicyCreatedEvent(PolicyCreatedEvent event) {
        publish(PolicyConstants.KafkaTopic.POLICY_CREATED, event.policyId(), event);
    }

    /**
     * 处理保单激活事件
     */
    @EventHandler
    public void handlePolicyActivatedEvent(PolicyActivatedEvent event) {
        publish(PolicyConstants.KafkaTopic.POLICY_ACTIVATED, event.policyId(), event);
    }

    /**
     * 处理保单签发事件：外发到 Kafka，供监管域采集承保数据、再保域触发自动分保等下游消费。
     */
    @EventHandler
    public void handlePolicyIssuedEvent(PolicyIssuedEvent event) {
        publish(PolicyConstants.KafkaTopic.POLICY_ISSUED, event.policyId(), event);
    }

    /**
     * 序列化并发布到 Kafka，等待 broker 确认。
     * <p>
     * 🔴 失败（broker 不可达、确认超时、主题无权限）会抛 {@code KafkaPublishException} —— 这是
     * 「失败可见 → 入 DLQ → 定时重投」链路的触发点。原先发后即弃 future 的写法会让失败静默丢失：
     * 既不重试、也不留痕、也无从对账。
     * </p>
     */
    private void publish(String topic, String key, Object payload) {
        String eventJson = JSON.toJSONString(payload);
        KafkaPublishSupport.awaitSent(() -> kafkaTemplate.send(topic, key, eventJson), topic, key);
    }
}
