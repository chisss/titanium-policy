package com.titanium.policy.query.scheduled;

import java.util.Optional;

import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.messaging.deadletter.SequencedDeadLetterProcessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * 死信队列监控 + 重试服务
 * <p>
 * 定时扫描保单读模型处理组的死信队列（DLQ），重试此前失败的事件。配合 bootstrap
 * 中开启的 DLQ 配置使用，保证读模型投影的最终一致性。
 * </p>
 * <p>
 * <b>修复说明</b>：原实现使用了 Axon 4.10 不存在的 API（{@code TrackingEventProcessor.deadLetterQueue()}、
 * {@code DeadLetterQueue} 类）。现改用官方
 * {@link SequencedDeadLetterProcessor#processAny()} 重试死信序列。
 * </p>
 *
 * @author wei.sun
 * @since 2026/1/27
 */
@Slf4j
@Service
public class DeadLetterQueueService {

    /** 投影处理组名，与读模型 handler 的 @ProcessingGroup 一致 */
    private static final String PROCESSING_GROUP = "policy-query-group";

    private final EventProcessingConfiguration eventProcessingConfig;

    public DeadLetterQueueService(EventProcessingConfiguration eventProcessingConfig) {
        this.eventProcessingConfig = eventProcessingConfig;
    }

    /**
     * 定时扫描死信队列并重试失败事件（每30秒一次）
     * <p>
     * {@link SequencedDeadLetterProcessor#processAny()} 会取出任意一个待处理的死信序列尝试重新投递， 成功则从
     * DLQ 移除，失败则保留待下次重试。
     * </p>
     */
    @Scheduled(fixedRate = 30000)
    public void retryDeadLetterEvents() {
        Optional<SequencedDeadLetterProcessor<org.axonframework.eventhandling.EventMessage<?>>> processorOpt =
                eventProcessingConfig.sequencedDeadLetterProcessor(PROCESSING_GROUP);

        if (processorOpt.isEmpty()) {
            log.debug("处理组 {} 未启用死信队列，跳过重试", PROCESSING_GROUP);
            return;
        }

        SequencedDeadLetterProcessor<org.axonframework.eventhandling.EventMessage<?>> processor = processorOpt.get();
        try {
            boolean processed = processor.processAny();
            if (processed) {
                log.info("死信队列重试成功一条序列: group={}", PROCESSING_GROUP);
            } else {
                log.debug("死信队列为空或无可重试序列: group={}", PROCESSING_GROUP);
            }
        } catch (Exception e) {
            log.error("死信队列重试异常: group={}", PROCESSING_GROUP, e);
        }
    }
}
