package com.titanium.policy.bootstrap.config;


import javax.sql.DataSource;

import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.jpa.JpaTokenStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jpa.SQLErrorCodesResolver;
import org.axonframework.serialization.Serializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Axon JPA EventStore 配置
 * <p>
 * 使用JPA作为事件存储引擎，将事件持久化到MySQL数据库。
 * 禁用AxonServer后必须提供EventStorageEngine实现。
 * </p>
 */
@Configuration
public class AxonJpaEventStoreConfig {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * EntityManager提供者
     */
    @Bean
    public EntityManagerProvider entityManagerProvider() {
        return () -> entityManager;
    }

    /**
     * 持久化异常解析器（使用MySQL的SQL错误码映射）
     */
    @Bean
    public PersistenceExceptionResolver persistenceExceptionResolver(DataSource dataSource) {
        try {
            return new SQLErrorCodesResolver(dataSource);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create PersistenceExceptionResolver", e);
        }
    }

    /**
     * 配置基于JPA的事件存储引擎
     */
    @Bean
    public EventStorageEngine eventStorageEngine(
            Serializer defaultSerializer,
            PersistenceExceptionResolver persistenceExceptionResolver,
            EntityManagerProvider entityManagerProvider,
            TransactionManager transactionManager) {

        return JpaEventStorageEngine.builder()
                .snapshotSerializer(defaultSerializer)
                .upcasterChain(org.axonframework.serialization.upcasting.event.NoOpEventUpcaster.INSTANCE)
                .persistenceExceptionResolver(persistenceExceptionResolver)
                .eventSerializer(defaultSerializer)
                .snapshotFilter(e -> true)
                .transactionManager(transactionManager)
                .entityManagerProvider(entityManagerProvider)
                .build();
    }

    /**
     * 配置Token Store用于追踪事件处理器位置
     */
    @Bean
    public TokenStore tokenStore(
            EntityManagerProvider entityManagerProvider,
            Serializer serializer) {
        return JpaTokenStore.builder()
                .entityManagerProvider(entityManagerProvider)
                .serializer(serializer)
                .build();
    }
}
