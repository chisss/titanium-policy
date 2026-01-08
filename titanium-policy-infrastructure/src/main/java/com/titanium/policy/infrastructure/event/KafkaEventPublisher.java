package com.titanium.policy.infrastructure.event;

import com.alibaba.fastjson2.JSON;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.titanium.policy.common.constant.PolicyConstants;
import com.titanium.policy.event.PolicyActivatedEvent;
import com.titanium.policy.event.PolicyCreatedEvent;

/**
 * Kafka事件发布器，用于将领域事件发布到Kafka
 */
@Component
public class KafkaEventPublisher {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

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
}
