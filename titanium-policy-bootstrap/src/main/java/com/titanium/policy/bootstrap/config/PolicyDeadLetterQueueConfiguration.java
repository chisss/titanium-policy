package com.titanium.policy.bootstrap.config;

import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.deadletter.jpa.JpaSequencedDeadLetterQueue;
import org.axonframework.serialization.Serializer;
import org.axonframework.springboot.EventProcessorProperties;
import org.axonframework.springboot.util.DeadLetterQueueProviderConfigurerModule;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 保单域事件处理器死信队列配置。
 *
 * <p>Axon 4.10 的 JPA 自动配置在 Spring Boot 4 环境中未生效，因此由保单服务组合根显式使用
 * 现有 EntityManager 与事务管理器装配 JPA 死信队列。</p>
 */
@Configuration(proxyBeanMethods = false)
public class PolicyDeadLetterQueueConfiguration {

    /**
     * 为配置中启用 DLQ 的处理组提供 JPA 持久化死信队列。
     */
    @Bean
    @ConditionalOnMissingBean(DeadLetterQueueProviderConfigurerModule.class)
    public DeadLetterQueueProviderConfigurerModule policyDeadLetterQueueProviderConfigurerModule(
            EventProcessorProperties eventProcessorProperties,
            EntityManagerProvider entityManagerProvider,
            TransactionManager transactionManager,
            @Qualifier("serializer") Serializer genericSerializer,
            @Qualifier("eventSerializer") Serializer eventSerializer) {
        return new DeadLetterQueueProviderConfigurerModule(
                eventProcessorProperties,
                processingGroup -> configuration -> JpaSequencedDeadLetterQueue.<EventMessage<?>>builder()
                        .processingGroup(processingGroup)
                        .entityManagerProvider(entityManagerProvider)
                        .transactionManager(transactionManager)
                        .genericSerializer(genericSerializer)
                        .eventSerializer(eventSerializer)
                        .build());
    }
}
