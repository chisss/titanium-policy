package com.titanium.policy.bootstrap.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.SimpleSerializedType;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.bootstrap.config.AxonSerializerConfig;
import com.titanium.policy.event.PolicyActivatedEvent;
import com.titanium.policy.event.insurance.InsuranceCreatedEvent;

/**
 * 事件 @Revision 存量兼容测试（m26-01 / AXN-01a 任务(4)）。
 * <p>
 * 证明三件事：
 * <ol>
 *   <li>写侧：@Revision 注解经默认 AnnotationRevisionResolver 进入序列化类型标识
 *       （PolicyActivatedEvent="1"，InsuranceCreatedEvent 保持 "2"）；</li>
 *   <li>读侧：补注解前写入的存量事件（payload_revision 列为 NULL）仍可正常反序列化——往返相等；</li>
 *   <li>机制真相锁定：revision 值（null/"1"/"99"）不参与且不影响反序列化结果
 *       （Axon 4.10 中 revision 唯一消费者是 Upcaster 链的 canUpcast，
 *       见 docs/技术文档/事件版本演化与Upcaster规约-2026-10.md §2.2；
 *       本用例可防止未来有人在反序列化路径硬编码 revision 检查而改变存量可加载性）。</li>
 * </ol>
 * 🔴 序列化器直接引用生产实现 {@link AxonSerializerConfig#buildObjectMapper()}（m26-05 起为严格模式）：
 * 原测试侧复刻 lenient mapper 已随 lenient 删除一并废止，杜绝测试 mapper 与生产配置漂移。
 * </p>
 */
class EventRevisionCompatibilityTest {

    /** 事件载荷类型名（类全名，对应事件存储 payload_type 列）。 */
    private static final String EVENT_TYPE = PolicyActivatedEvent.class.getName();

    /** 生产严格 ObjectMapper（AxonSerializerConfig.buildObjectMapper，m26-05 起不关闭 FAIL_ON_UNKNOWN_PROPERTIES）。 */
    private final ObjectMapper objectMapper = AxonSerializerConfig.buildObjectMapper();

    private final Serializer serializer = JacksonSerializer.builder().objectMapper(objectMapper).build();

    /**
     * 写侧：@Revision 声明反映到序列化类型标识。
     * 默认 AnnotationRevisionResolver 读注解，policy 的 lenient 装配未定制 revisionResolver。
     */
    @Test
    void writtenTypeCarriesDeclaredRevision() {
        assertEquals("1", serializer.typeForClass(PolicyActivatedEvent.class).getRevision(),
                "PolicyActivatedEvent 应携带 @Revision(\"1\")");
        assertEquals("2", serializer.typeForClass(InsuranceCreatedEvent.class).getRevision(),
                "InsuranceCreatedEvent 应保持 @Revision(\"2\")（v1→v2 已由 Upcaster 承接）");
    }

    /**
     * 读侧：存量事件（revision=NULL 时期写入）经「同名类型 + null revision」重建后仍可加载，往返相等。
     * 模拟链路：AbstractEventEntry.getPayload() 照搬 DB 两列（payload_type + payload_revision=NULL）。
     */
    @Test
    void legacyNullRevisionPayloadRemainsLoadable() {
        PolicyActivatedEvent event = new PolicyActivatedEvent("POL-LEGACY-1", "INS-LEGACY-1", "BIZ-LEGACY-1",
                LocalDateTime.of(2026, 1, 1, 10, 0), "TENANT-LEGACY", new Money(new BigDecimal("100.00"), "CNY"));
        SerializedObject<byte[]> written = serializer.serialize(event, byte[].class);
        SimpleSerializedObject<byte[]> legacyPayload = new SimpleSerializedObject<>(written.getData(), byte[].class,
                new SimpleSerializedType(EVENT_TYPE, null));
        PolicyActivatedEvent loaded = serializer.deserialize(legacyPayload);
        assertEquals(event, loaded, "补 @Revision 不得改变存量 revision=NULL 事件的可加载性");
    }

    /**
     * 机制真相：revision 值不参与反序列化（resolveClassName 仅用类名），
     * 同一 payload 以 null/"1"/"99" 三态加载结果一致。
     */
    @Test
    void revisionValueDoesNotAffectDeserialization() {
        PolicyActivatedEvent event = new PolicyActivatedEvent("POL-LEGACY-2", "INS-LEGACY-2", "BIZ-LEGACY-2",
                LocalDateTime.of(2026, 2, 1, 9, 30), "TENANT-LEGACY", new Money(new BigDecimal("200.50"), "CNY"));
        SerializedObject<byte[]> written = serializer.serialize(event, byte[].class);
        for (String revision : new String[] {null, "1", "99"}) {
            SimpleSerializedObject<byte[]> variant = new SimpleSerializedObject<>(written.getData(), byte[].class,
                    new SimpleSerializedType(EVENT_TYPE, revision));
            PolicyActivatedEvent loaded = serializer.deserialize(variant);
            assertEquals(event, loaded, "revision=" + revision + " 不应影响反序列化结果（revision 只是 Upcaster 链的输入）");
        }
    }

    /**
     * 存量真实场景：@Revision 缺失时期写入的旧形态 payload（3 参构造时代，无 insuranceId/bizNo/totalPremium）
     * 以版本 null 加载成功，缺失字段补 null（Jackson 缺失字段容忍，R4 约束下不再新增此类演进）。
     */
    @Test
    void legacyPayloadWithoutNewFieldsLoadsWithNullDefaults() {
        String legacyJson = "{\"policyId\":\"POL-LEGACY-3\",\"activatedAt\":\"2026-01-01T10:00:00\","
                + "\"tenantId\":\"TENANT-LEGACY\"}";
        SimpleSerializedObject<byte[]> legacyPayload = new SimpleSerializedObject<>(
                legacyJson.getBytes(StandardCharsets.UTF_8), byte[].class,
                new SimpleSerializedType(EVENT_TYPE, null));
        PolicyActivatedEvent loaded = serializer.deserialize(legacyPayload);
        assertEquals("POL-LEGACY-3", loaded.policyId());
        assertEquals(LocalDateTime.of(2026, 1, 1, 10, 0), loaded.activatedAt());
        assertNull(loaded.insuranceId(), "旧形态缺 insuranceId，应补 null");
        assertNull(loaded.bizNo(), "旧形态缺 bizNo，应补 null");
        assertNull(loaded.totalPremium(), "历史事件无保费字段，应为 null（事件类 javadoc 契约）");
    }
}
