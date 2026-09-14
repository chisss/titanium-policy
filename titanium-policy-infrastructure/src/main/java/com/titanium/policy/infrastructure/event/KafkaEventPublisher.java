package com.titanium.policy.infrastructure.event;

import org.axonframework.eventhandling.EventHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;

import com.titanium.policy.common.constant.PolicyConstants;
import com.titanium.policy.event.PolicyActivatedEvent;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.event.PolicyIssuedEvent;

import lombok.AllArgsConstructor;

/**
 * Kafka事件发布器，用于将领域事件发布到Kafka
 */
@Component
@AllArgsConstructor
public class KafkaEventPublisher {

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
        String eventJson = JSON.toJSONString(event);
        kafkaTemplate.send(PolicyConstants.KafkaTopic.POLICY_CREATED, event.policyId(), eventJson);
    }

    /**
     * 处理保单激活事件
     */
    @EventHandler
    public void handlePolicyActivatedEvent(PolicyActivatedEvent event) {
        String eventJson = JSON.toJSONString(event);
        kafkaTemplate.send(PolicyConstants.KafkaTopic.POLICY_ACTIVATED, event.policyId(), eventJson);
    }

    /**
     * 处理保单签发事件：外发到 Kafka，供监管域采集承保数据、再保域触发自动分保等下游消费。
     */
    @EventHandler
    public void handlePolicyIssuedEvent(PolicyIssuedEvent event) {
        String eventJson = JSON.toJSONString(event);
        kafkaTemplate.send(PolicyConstants.KafkaTopic.POLICY_ISSUED, event.policyId(), eventJson);
    }
}
