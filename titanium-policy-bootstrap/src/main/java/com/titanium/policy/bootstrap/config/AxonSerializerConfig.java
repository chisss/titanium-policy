package com.titanium.policy.bootstrap.config;

import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Axon 序列化器配置（事件溯源向后兼容核心）
 * <p>
 * 🔴 <b>为何必须宽容未知字段</b>：领域值对象常带「派生 getter」（如 {@code Money.isZero()}、
 * {@code LineCoveragePeriod.isWholeLife()}），Jackson 序列化时把它们当额外属性写进事件 payload
 * （{@code "zero":false} / {@code "wholeLife":false}），而 record 上无对应构造参数，反序列化即抛
 * {@code UnrecognizedPropertyException} → <b>投影永久失败、读模型永远为空</b>（本次故障根因）。
 * </p>
 * <p>
 * 事件存储是不可变历史真相，不能靠删事件"修数据"。故在反序列化侧关闭
 * {@code FAIL_ON_UNKNOWN_PROPERTIES}：既让存量事件可正常重放，也让未来新增派生 getter 不再毒化
 * 事件流——这是事件溯源系统的标准向后兼容策略。
 * </p>
 * <p>
 * 🔴 <b>必须按限定名覆盖三个序列化器</b>：Axon 自动配置分别注册 {@code serializer}（general）、
 * {@code messageSerializer}、{@code eventSerializer} 三个 {@link Serializer} bean，并以
 * {@code @Qualifier} 区分。仅提供 {@code @Primary} 的通用 bean <b>不会</b>覆盖
 * {@code eventSerializer}，事件仍走默认严格序列化器（已验证：只加 @Primary 时投影依旧全失败）。
 * </p>
 * <p>
 * 配合派生 getter 上的 {@code @JsonIgnore} 形成双层防护：注解让新事件 payload 干净，本配置兜住
 * 存量事件与漏标注的情况。
 * </p>
 */
@Configuration
public class AxonSerializerConfig {

    /**
     * 构造宽容 ObjectMapper（三个序列化器共用同一套规则）。
     *
     * @return 忽略未知字段、支持 JSR-310 的 ObjectMapper
     */
    private ObjectMapper lenientObjectMapper() {
        return new ObjectMapper()
                // 支持 LocalDateTime 等 JSR-310 时间类型（Axon 4.10 依赖 Jackson 2.x）
                .registerModule(new JavaTimeModule())
                // 🔴 关键：容忍 payload 中的派生字段，保证存量事件可重放
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                // 时间按 ISO-8601 字符串而非时间戳数组，便于人工排查事件内容
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                // 不序列化 null，压缩事件体积
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    /**
     * 通用序列化器（快照、Saga 状态等）。
     *
     * @return 宽容序列化器
     */
    @Bean
    @Primary
    public Serializer serializer() {
        return JacksonSerializer.builder().objectMapper(lenientObjectMapper()).build();
    }

    /**
     * 事件序列化器（事件存储读写，投影链路的关键）。
     *
     * @return 宽容序列化器
     */
    @Bean
    @Qualifier("eventSerializer")
    public Serializer eventSerializer() {
        return JacksonSerializer.builder().objectMapper(lenientObjectMapper()).build();
    }

    /**
     * 消息序列化器（命令/查询消息载荷）。
     *
     * @return 宽容序列化器
     */
    @Bean
    @Qualifier("messageSerializer")
    public Serializer messageSerializer() {
        return JacksonSerializer.builder().objectMapper(lenientObjectMapper()).build();
    }
}
