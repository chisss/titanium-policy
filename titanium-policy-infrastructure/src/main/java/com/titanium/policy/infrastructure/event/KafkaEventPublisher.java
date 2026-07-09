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
     * 处理保单创建事件
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
