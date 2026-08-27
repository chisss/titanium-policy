package com.titanium.policy.bootstrap.config;


import javax.sql.DataSource;

import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.Configurer;
import org.axonframework.config.ConfigurerModule;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.jpa.JpaTokenStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jpa.SQLErrorCodesResolver;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.axonframework.modelling.saga.repository.jpa.JpaSagaStore;
import org.axonframework.serialization.Serializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.titanium.policy.bootstrap.upcaster.InsuranceCreatedEventUpcaster;

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
            TransactionManager transactionManager,
            InsuranceCreatedEventUpcaster insuranceCreatedEventUpcaster) {

        return JpaEventStorageEngine.builder()
                .snapshotSerializer(defaultSerializer)
                .upcasterChain(insuranceCreatedEventUpcaster)
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

    /**
     * 持久化 Saga 状态，避免容器重启丢失正在进行的出单编排。
     */
    @Bean
    public SagaStore<Object> sagaStore(EntityManagerProvider entityManagerProvider, Serializer serializer) {
        return JpaSagaStore.builder()
                .entityManagerProvider(entityManagerProvider)
                .serializer(serializer)
                .build();
    }

    /**
     * Spring Boot 4 下 Axon 的 JPA 自动配置可能因条件不匹配而回退到内存 Saga Store，
     * 这里显式把 JPA Store 注册到事件处理配置，确保运行态选择与数据库表一致。
     */
    @Bean
    public ConfigurerModule policyJpaSagaStoreConfigurerModule() {
        return new ConfigurerModule() {
            @Override
            public void configureModule(Configurer configurer) {
                configurer.eventProcessing().registerSagaStore(configuration ->
                        configuration.getComponent(SagaStore.class));
            }
        };
    }
}
