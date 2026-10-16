package com.titanium.policy.bootstrap.config;

import org.axonframework.eventsourcing.EventCountSnapshotTriggerDefinition;
import org.axonframework.eventsourcing.SnapshotTriggerDefinition;
import org.axonframework.eventsourcing.Snapshotter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Axon Framework 配置类（快照治理，m26-04）
 * <p>
 * 🔴 **为何放 bootstrap 而非 infrastructure**：本域基础设施模块不依赖 Axon（零 axon 编译依赖），Axon 相关装配
 * （序列化器、事件存储）本就集中在 bootstrap 的 {@code config} 包——本类与 {@code AxonSerializerConfig} /
 * {@code AxonJpaEventStoreConfig} 同处，保持「本域 Axon 装配单点」。
 * </p>
 * <p>
 * 本域四个聚合（Policy / PolicyLoan / Insurance / Proposal）均为纯自动装配（全仓扫描确认无显式 {@code Repository}
 * Bean），故快照触发器经聚合根 {@code @Aggregate(snapshotTriggerDefinition = "policySnapshotTriggerDefinition")}
 * 按 Bean 名引用本类所声明的 Bean。Axon 的 Spring 集成以 {@code getBean(name, type)} 解析该属性值，Bean 名写错会在**启动期**以
 * {@code NoSuchBeanDefinitionException} 快速失败，不会静默失效。
 * </p>
 */
@Configuration
public class AxonConfig {

    /**
     * 快照触发阈值：聚合累计事件数达到该值即生成快照。
     * <p>
     * m26-04 真机实测（2026-10-16）：本域单聚合累计事件数最多 32（PolicyLoan，逐条贷款事件高频累积），Policy 23、Insurance
     * 4、Proposal 3，均未达 50。取 50 与全域统一口径一致（全仓实测最大为 regulatory 的 RegulatoryReport 54）——存量聚合零快照开销，
     * 同时为事件流持续增长兜底：越过阈值后聚合加载只需「最新快照 + 增量事件」，不再重放完整事件流。
     * </p>
     */
    private static final int SNAPSHOT_TRIGGER_THRESHOLD = 50;

    /**
     * 保单域快照触发器（Policy / PolicyLoan / Insurance / Proposal 四聚合共用）。
     * <p>
     * {@link EventCountSnapshotTriggerDefinition} 自身无状态——每次为聚合实例创建独立计数触发器，多聚合共享同一 Bean 安全。
     * </p>
     */
    @Bean
    public SnapshotTriggerDefinition policySnapshotTriggerDefinition(Snapshotter snapshotter) {
        return new EventCountSnapshotTriggerDefinition(snapshotter, SNAPSHOT_TRIGGER_THRESHOLD);
    }
}
