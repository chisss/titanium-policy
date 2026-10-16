package com.titanium.policy.bootstrap.config;

import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.titanium.common.json.JacksonMappers;

/**
 * Axon 序列化器配置（事件溯源的严格模式与历史载荷承接）
 * <p>
 * 🔴 <b>严格模式（m26-05，2026-10-16）</b>：不关闭 {@code FAIL_ON_UNKNOWN_PROPERTIES}
 * （Jackson 默认 true）——payload 中出现 record 上不存在的属性即抛
 * {@code UnrecognizedPropertyException}，让 schema 漂移在读取时立即暴露，而非被静默忽略。
 * 与全域其余 16 域的 Axon 默认装配（yml {@code axon.serializer.general=jackson} +
 * {@code defaultAxonObjectMapper}）同为严格口径（m26-05 前置步经 Axon 4.10 字节码级实证）。
 * </p>
 * <p>
 * <b>历史沿革（为何曾经宽容）</b>：领域值对象常带「派生 getter」（如 {@code Money.isZero()}、
 * {@code LineCoveragePeriod.isWholeLife()}），Jackson 序列化时把它们当额外属性写进事件 payload
 * （{@code "zero":false} / {@code "wholeLife":false}），record 上无对应构造参数，严格反序列化即抛
 * {@code UnrecognizedPropertyException} → <b>投影永久失败、读模型永远为空</b>（2026-08 故障根因）。
 * 当时以全局 lenient（{@code disable(FAIL_ON_UNKNOWN_PROPERTIES)}，c92a62f）兜底，并为派生 getter
 * 逐处补 {@code @JsonIgnore} 让新 payload 干净。
 * </p>
 * <p>
 * <b>严格化后的承接（m26-05）</b>：存量脏 payload（@JsonIgnore 之前写入）由
 * {@code PolicyCreatedEventLegacyKeyStripUpcaster} 在事件读取链上显式剥离承接（全量 800 条存量回放
 * 实测）；事件存储是不可变历史真相，不靠删事件"修数据"。今后新增派生 getter 必须补
 * {@code @JsonIgnore}；事件结构变更走「{@code @Revision} 递增 + Upcaster + CHANGELOG」三同批
 * （见 docs/技术文档/事件版本演化与Upcaster规约-2026-10.md）。
 * </p>
 * <p>
 * 🔴 <b>必须按限定名覆盖三个序列化器</b>：Axon 自动配置分别注册 {@code serializer}（general）、
 * {@code messageSerializer}、{@code eventSerializer} 三个 {@link Serializer} bean，并以
 * {@code @Qualifier} 区分。仅提供 {@code @Primary} 的通用 bean <b>不会</b>覆盖
 * {@code eventSerializer}，事件仍走默认严格序列化器（已验证：只加 @Primary 时投影依旧全失败）。
 * </p>
 */
@Configuration
public class AxonSerializerConfig {

    /**
     * 构造严格 ObjectMapper（三个序列化器共用同一套规则）。
     * <p>
     * 🔴 不关闭 {@code FAIL_ON_UNKNOWN_PROPERTIES}（Jackson 默认 true = 严格）——与全域其余 16 域
     * Axon 默认装配的 {@code defaultAxonObjectMapper}（同为裸 {@code new ObjectMapper()}）同一口径。
     * </p>
     * <p>
     * public static 工厂：生产装配与测试（严格性判据、存量全量回放）共用同一实现，
     * 杜绝测试侧复刻 mapper 随生产配置漂移（m26-05 判据锚点）。
     * </p>
     *
     * @return 严格（拒绝未知属性）、支持 JSR-310 的 ObjectMapper
     */
    // ✅ m22-03（审查报告 SPR-04）定性：本处**有意独立构造**——Axon 事件序列化需要**固定的
    // JSR-310 + ISO 时间 + NON_NULL 策略**（与 Web 层 MVC 序列化策略分离），不能共用 Web 层 ObjectMapper。
    // ✅ m26-06（AXN-05）续：**独立构造保留**——Axon 自带的 ObjectMapper bean 经 javap 实证为
    // 「裸构造 + findAndRegisterModules」，既不禁用时间戳、也不忽略 null，复用即改事件存储读写线格式；
    // 但构造点收口到统一工厂 JacksonMappers.axonSerialization()，其配置与本处改造前**逐字等价**。
    // 🔴 严格模式（不 disable(FAIL_ON_UNKNOWN_PROPERTIES)）是 m26-05 的刻意选择，见类 javadoc 与下方方法 javadoc；
    // 新增派生 getter 必须补 @JsonIgnore。
    public static ObjectMapper buildObjectMapper() {
        return JacksonMappers.axonSerialization();
    }

    /**
     * 通用序列化器（快照、Saga 状态等）。
     *
     * @return 严格序列化器
     */
    @Bean
    @Primary
    public Serializer serializer() {
        return JacksonSerializer.builder().objectMapper(buildObjectMapper()).build();
    }

    /**
     * 事件序列化器（事件存储读写，投影链路的关键）。
     *
     * @return 严格序列化器
     */
    @Bean
    @Qualifier("eventSerializer")
    public Serializer eventSerializer() {
        return JacksonSerializer.builder().objectMapper(buildObjectMapper()).build();
    }

    /**
     * 消息序列化器（命令/查询消息载荷）。
     *
     * @return 严格序列化器
     */
    @Bean
    @Qualifier("messageSerializer")
    public Serializer messageSerializer() {
        return JacksonSerializer.builder().objectMapper(buildObjectMapper()).build();
    }
}
