package com.titanium.policy.infrastructure.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import org.axonframework.config.ProcessingGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import com.titanium.common.kafka.KafkaPublishException;
import com.titanium.policy.common.constant.PolicyConstants;
import com.titanium.policy.event.PolicyActivatedEvent;
import com.titanium.policy.event.PolicyIssuedEvent;

/**
 * 保单出站发布器用例。
 *
 * <p>锁死两件事：① 主题与分区键（键须与消费端回写维度一致）；② 🔴 <b>发布失败必须抛出</b>——
 * 出站处理组为 tracking + DLQ 形态，Axon 只对「处理器抛出的异常」入队死信；若失败仅记日志，
 * 事件既不重投也不留痕，等于静默丢失。这是「失败可见 → 入 DLQ → 定时重投」链路的触发点。</p>
 */
class KafkaEventPublisherTest {

    private static final String POLICY_ID = "P-001";
    private static final String TENANT_ID = "TENANT-001";
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 9, 14, 10, 0, 0);

    private KafkaTemplate<String, String> kafkaTemplate;

    private KafkaEventPublisher publisher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        publisher = new KafkaEventPublisher(kafkaTemplate);
    }

    @Test
    @DisplayName("保单激活外发：policyId 为分区键，载荷为含租户ID的 JSON 对象")
    void shouldPublishActivatedEventWithPolicyIdAsKey() {
        stubSendSuccess();

        publisher.handlePolicyActivatedEvent(new PolicyActivatedEvent(POLICY_ID, OCCURRED_AT, TENANT_ID));

        JSONObject payload = capturedPayload(PolicyConstants.KafkaTopic.POLICY_ACTIVATED);
        assertEquals(POLICY_ID, payload.getString("policyId"));
        assertEquals(TENANT_ID, payload.getString("tenantId"));
    }

    @Test
    @DisplayName("保单签发外发：policyId 为分区键，载荷含租户ID")
    void shouldPublishIssuedEventWithPolicyIdAsKey() {
        stubSendSuccess();

        publisher.handlePolicyIssuedEvent(
                new PolicyIssuedEvent(POLICY_ID, "PN-001", "PROD-001", null, null, OCCURRED_AT, "tester",
                        TENANT_ID));

        JSONObject payload = capturedPayload(PolicyConstants.KafkaTopic.POLICY_ISSUED);
        assertEquals(POLICY_ID, payload.getString("policyId"));
        assertEquals(TENANT_ID, payload.getString("tenantId"));
    }

    @Test
    @DisplayName("🔴 发布失败必须抛出（不再静默丢弃），否则事件不会进死信队列、无法重投")
    void shouldThrowWhenSendFails() {
        CompletableFuture<SendResult<String, String>> failed =
                CompletableFuture.failedFuture(new RuntimeException("broker unreachable"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failed);

        PolicyActivatedEvent event = new PolicyActivatedEvent(POLICY_ID, OCCURRED_AT, TENANT_ID);

        KafkaPublishException exception = assertThrows(KafkaPublishException.class,
                () -> publisher.handlePolicyActivatedEvent(event));

        assertTrue(exception.getMessage().contains(PolicyConstants.KafkaTopic.POLICY_ACTIVATED),
                "异常须携带主题以便定位");
        assertNotNull(exception.getCause(), "须保留底层失败原因");
    }

    @Test
    @DisplayName("出站处理组名与 @ProcessingGroup 一致（漂移会使 DLQ 与首启位点配置同时落空）")
    void shouldDeclareProcessingGroupConsistently() {
        ProcessingGroup annotation = KafkaEventPublisher.class.getAnnotation(ProcessingGroup.class);

        assertNotNull(annotation, "出站发布器必须声明 @ProcessingGroup");
        assertEquals(KafkaEventPublisher.PROCESSING_GROUP, annotation.value());
        assertEquals("policy-kafka-group", KafkaEventPublisher.PROCESSING_GROUP,
                "组名须与 application.yml 的 axon.eventhandling.processors 键一致");
    }

    @SuppressWarnings("unchecked")
    private void stubSendSuccess() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    /**
     * 捕获发布器交给 KafkaTemplate 的载荷原文（第三实参）并解析为 JSON 对象。
     */
    private JSONObject capturedPayload(String topic) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(anyString(), anyString(), captor.capture());
        String payload = captor.getValue();
        assertEquals('{', payload.charAt(0), "交给 KafkaTemplate 的必须是 JSON 对象文本");
        return JSON.parseObject(payload);
    }
}
