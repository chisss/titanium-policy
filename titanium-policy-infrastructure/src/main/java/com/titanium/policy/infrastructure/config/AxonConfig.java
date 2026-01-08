package com.titanium.policy.infrastructure.config;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.TrackingEventProcessorConfiguration;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Axon配置类
 */
@Configuration
public class AxonConfig {

    /**
     * 配置命令总线
     */
    @Bean
    public CommandBus commandBus() {
        return SimpleCommandBus.builder().build();
    }

    /**
     * 配置事件总线
     */
    @Bean
    public EventBus eventBus() {
        return SimpleEventBus.builder().build();
    }

    /**
     * 配置事件存储引擎
     */
    @Bean
    public EventStorageEngine eventStorageEngine() {
        return new InMemoryEventStorageEngine();
    }

    /**
     * 配置嵌入式事件存储
     */
    @Bean
    public EmbeddedEventStore eventStore(EventStorageEngine storageEngine) {
        return EmbeddedEventStore.builder().storageEngine(storageEngine).build();
    }

    /**
     * 配置跟踪事件处理器
     */
    @Bean
    public TrackingEventProcessorConfiguration trackingEventProcessorConfiguration() {
        // 方式1：单线程处理（简单场景）
        return TrackingEventProcessorConfiguration.forSingleThreadedProcessing();
    }
}
