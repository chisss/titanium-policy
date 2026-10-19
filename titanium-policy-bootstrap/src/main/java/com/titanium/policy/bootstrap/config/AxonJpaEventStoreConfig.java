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
import org.axonframework.serialization.upcasting.event.EventUpcasterChain;
import org.axonframework.spring.messaging.unitofwork.SpringTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.titanium.policy.bootstrap.upcaster.InsuranceCreatedEventUpcaster;
import com.titanium.policy.bootstrap.upcaster.PolicyCreatedEventLegacyKeyStripUpcaster;

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
     * Axon 事务管理器：显式绑定 Spring 的 {@link PlatformTransactionManager}（本域实际为 JPA 事务管理器）。
     * <p>
     * 🔴 不可依赖 Axon 的 {@code TransactionAutoConfiguration} 自动提供：它同样以 Boot 4 已迁包的旧类名声明
     * {@code @AutoConfigureAfter}，评估早于 JPA 自动配置注册 {@code transactionManager} 的时机，其
     * {@code @ConditionalOnBean(PlatformTransactionManager)} 遂不匹配，随后由
     * {@code NoOpTransactionAutoConfiguration} 兜底注入 {@code NoOpTransactionManager}。
     * 2026-10-19 条件评估报告实测：{@code TransactionAutoConfiguration#axonTransactionManager} did not match —
     * 「did not find any beans of type PlatformTransactionManager」，而
     * {@code NoOpTransactionAutoConfiguration#axonTransactionManager} matched。
     * </p>
     * <p>
     * NoOp 下 {@code JpaEventStorageEngine.appendEvents} 不在事务内执行，真实写事件即抛
     * {@code TransactionRequiredException: No EntityManager with actual transaction available for current thread}
     * （payment 域 {@code PaymentDeadLetterResilienceTest} 首次真实写事件时暴露，本域同步修复）。
     * </p>
     */
    @Bean
    public TransactionManager axonTransactionManager(PlatformTransactionManager platformTransactionManager) {
        return new SpringTransactionManager(platformTransactionManager);
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
     * <p>
     * 🔴 Upcaster 链注册点（m26-05）：新增 Upcaster 必须在此注册才生效——「写了 Upcaster ≠ Upcaster 生效」，
     * 链与 {@code LegacyEventReplayTest} 测试拼链的一致性由该测试的源码断言钉住。
     * </p>
     */
    @Bean
    public EventStorageEngine eventStorageEngine(
            Serializer defaultSerializer,
            PersistenceExceptionResolver persistenceExceptionResolver,
            EntityManagerProvider entityManagerProvider,
            TransactionManager transactionManager,
            InsuranceCreatedEventUpcaster insuranceCreatedEventUpcaster,
            PolicyCreatedEventLegacyKeyStripUpcaster policyCreatedEventLegacyKeyStripUpcaster) {

        return JpaEventStorageEngine.builder()
                .snapshotSerializer(defaultSerializer)
                // JpaEventStorageEngine.Builder#upcasterChain 为单参：多 Upcaster 用 EventUpcasterChain 组装
                // （顺序即链序，逐级传递）
                .upcasterChain(new EventUpcasterChain(
                        insuranceCreatedEventUpcaster, policyCreatedEventLegacyKeyStripUpcaster))
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
