package com.titanium.policy.query.scheduled;

import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * @author wei.sun
 * @apiNote 死信队列监控+重试服务
 * @since 2026/1/27 15:34
 */
@Service
public class DeadLetterQueueService {
    private final EventProcessingConfiguration eventProcessingConfig;

    public DeadLetterQueueService(EventProcessingConfiguration eventProcessingConfig) {
        this.eventProcessingConfig = eventProcessingConfig;
    }

    // 定时扫描死信队列，重试失败事件
    @Scheduled(fixedRate = 30000) // 每30秒扫描一次
    public void retryDeadLetterEvents() {
        // 获取policy-query-group的死信队列
        EventProcessor processor = eventProcessingConfig.eventProcessor(
                "policy-query-group", EventProcessor.class
        );
        if (processor instanceof TrackingEventProcessor tep) {
            DeadLetterQueue<? extends EventMessage<?>> dlq = tep.deadLetterQueue();
            // 遍历死信队列，尝试重试
            dlq.forEach(deadLetter -> {
                try {
                    // 手动重试消费
                    tep.process(deadLetter);
                    dlq.remove(deadLetter); // 重试成功则移除
                } catch (Exception e) {
                    // 记录日志+告警，人工介入
                    log.error("死信事件重试失败：{}", deadLetter.getEvent().getPayloadType(), e);
                }
            });
        }
    }
}
