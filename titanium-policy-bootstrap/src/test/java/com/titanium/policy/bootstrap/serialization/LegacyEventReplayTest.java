package com.titanium.policy.bootstrap.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.beans.Introspector;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.axonframework.eventhandling.GenericDomainEventEntry;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.upcasting.event.EventUpcasterChain;
import org.axonframework.serialization.upcasting.event.InitialEventRepresentation;
import org.axonframework.serialization.upcasting.event.IntermediateEventRepresentation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

import com.titanium.policy.bootstrap.config.AxonSerializerConfig;
import com.titanium.policy.bootstrap.upcaster.InsuranceCreatedEventUpcaster;
import com.titanium.policy.bootstrap.upcaster.PolicyCreatedEventLegacyKeyStripUpcaster;
import com.titanium.policy.event.PolicyCreatedEvent;

/**
 * 存量事件严格模式回放测试（m26-05 / AXN-04：删除 lenient 序列化器后的事件可加载性验收）。
 * <p>
 * 四条测试 × 四类注入各有钉子（validate-m26-05.sh probe 节逐臂验证）：
 * <table>
 *   <tr><th>测试</th><th>重新 lenient</th><th>删测试链 Upcaster</th><th>删生产注册</th></tr>
 *   <tr><td>{@link #legacyPolicyPeriodFieldsAreRejectedWithoutUpcaster}</td><td>红</td><td>绿</td><td>绿</td></tr>
 *   <tr><td>{@link #upcasterChainStripsLegacyPolicyPeriodFields}</td><td>绿</td><td>红</td><td>绿</td></tr>
 *   <tr><td>{@link #productionEventStoreChainRegistersAllUpcasters}</td><td>绿</td><td>绿</td><td>红</td></tr>
 *   <tr><td>{@link #replaysAllPersistedPolicyEvents}</td><td>绿</td><td>红</td><td>绿</td></tr>
 * </table>
 * <p>
 * 🔴 <b>禁止在本类复制 mapper / 链的构造逻辑</b>：一律引用生产实现
 * （{@link AxonSerializerConfig#buildObjectMapper()} + 生产 Upcaster 类），杜绝测试侧复刻随生产配置漂移。
 * 链与生产注册的一致性由 {@link #productionEventStoreChainRegistersAllUpcasters} 源码断言钉住
 * （「写了 Upcaster ≠ Upcaster 生效」，见 Upcaster配套机制与注册规范-2026-10.md §二）。
 * </p>
 * <p>
 * 🔴 <b>失败面实测口径（2026-10-16 全量回放，供后续维护者对齐）</b>：全量 800 条存量中 83 条曾失败，
 * 全部为 {@code PolicyCreatedEvent}，唯一报错键 = {@code policyPeriod} 的 4 个已删除字段
 * （G11/g11-04 下沉段级遗留）。派生 getter 键（{@code "zero"} 等）因 {@code @JsonIgnore} 已是
 * Jackson 的 ignorable property（读侧静默跳过），<b>不</b>触发严格报错、无需 Upcaster 承接——
 * 这已由 {@link #upcasterChainStripsLegacyPolicyPeriodFields} 的混合样本锁定。
 * </p>
 */
class LegacyEventReplayTest {

    /** 全量 dump 快照路径系统属性（validate-m26-05.sh 传入）。 */
    private static final String DUMP_PROPERTY = "m26.replay.dump";

    /** m26-05 快照实测行数（policy_db.axon_domain_event_entry 全量，2026-10-16）。下界断言防收集面塌缩。 */
    private static final int EXPECTED_DUMP_ROWS = 800;

    private static final String POLICY_CREATED_EVENT = PolicyCreatedEvent.class.getName();

    private static final String EVENT_STORE_CONFIG = "src/main/java/com/titanium/policy/bootstrap/config/"
            + "AxonJpaEventStoreConfig.java";

    /**
     * 真实存量形态载荷（与 dump index=13 同形）：{@code policyPeriod} 含 4 个已删除键。
     * 日期为时间戳数组形态（历史 mapper 未 disable {@code WRITE_DATES_AS_TIMESTAMPS} 时写入，
     * JavaTimeModule 读侧两种形态均可解析）。
     */
    private static final String LEGACY_POLICY_PERIOD_PAYLOAD = """
            {
              "policyId": "POL-M26-05-LEGACY",
              "policyPeriod": {
                "insurancePeriodStart": [2026,8,11,0,0],
                "insurancePeriodEnd": [2046,8,6,23,59,59],
                "waitingPeriodDays": 0,
                "waitingPeriodEndDate": [2026,8,11,0,0],
                "hesitationPeriodDays": 15,
                "hesitationPeriodEndDate": [2026,8,26,0,0]
              },
              "sumInsured": {"value": 500000.0, "currency": "CNY", "zero": false}
            }
            """;

    /** 生产严格序列化器（与 {@code AxonJpaEventStoreConfig} 由同一 Spring 装配共用同一实现）。 */
    private static final Serializer SERIALIZER = JacksonSerializer.builder()
            .objectMapper(AxonSerializerConfig.buildObjectMapper())
            .build();

    /**
     * 生产 Upcaster 全集（与 {@code AxonJpaEventStoreConfig} 注册同批维护）。
     * 本清单是源码断言与测试拼链的<b>单点</b>：新增 Upcaster 只改这里 + 生产注册两处。
     */
    private static final List<EventUpcaster> UPCASTERS = List.of(
            new InsuranceCreatedEventUpcaster(),
            new PolicyCreatedEventLegacyKeyStripUpcaster());

    /** 生产同形链（与 {@code AxonJpaEventStoreConfig} 的 {@code EventUpcasterChain} 组装同构）。 */
    private static final EventUpcasterChain CHAIN = new EventUpcasterChain(UPCASTERS);

    /**
     * 严格性钉子 + 样本有牙证明：真实旧形态载荷（{@code policyPeriod} 含已删除键）<b>直读</b>必抛
     * {@code UnrecognizedPropertyException}。重新 lenient 则本用例判红（m26-05 核心变更不被静默回退）。
     */
    @Test
    void legacyPolicyPeriodFieldsAreRejectedWithoutUpcaster() {
        ObjectMapper mapper = AxonSerializerConfig.buildObjectMapper();

        UnrecognizedPropertyException exception = assertThrows(UnrecognizedPropertyException.class,
                () -> mapper.readValue(LEGACY_POLICY_PERIOD_PAYLOAD, PolicyCreatedEvent.class),
                "生产 mapper 必须是严格模式：无 Upcaster 承接的存量旧字段直读不得成功");

        assertEquals("waitingPeriodDays", exception.getPropertyName(),
                "应精确指向 policyPeriod 已删除字段（Jackson 报首个未知键）");
    }

    /**
     * 剥离承接：同一旧形态载荷经生产链（含 {@code PolicyCreatedEventLegacyKeyStripUpcaster}）后
     * 严格加载成功，且旧键在载荷层被显式移除（不依赖读成功反推）。
     * <p>
     * 样本混合两类历史键：{@code policyPeriod} 旧字段（需剥离）+ {@code sumInsured."zero"}
     * （{@code @JsonIgnore} 派生 getter 键，Jackson 静默容忍、无需剥离）——锁定失败面精确等于前者。
     * </p>
     */
    @Test
    void upcasterChainStripsLegacyPolicyPeriodFields() {
        IntermediateEventRepresentation upcasted = upcast(POLICY_CREATED_EVENT, null, LEGACY_POLICY_PERIOD_PAYLOAD);
        PolicyCreatedEvent event = SERIALIZER.deserialize(upcasted.getData(byte[].class));

        assertEquals("POL-M26-05-LEGACY", event.policyId());
        assertEquals(LocalDateTime.of(2026, 8, 11, 0, 0), event.policyPeriod().insurancePeriodStart());

        JsonNode strippedPeriod = upcasted.getData(JsonNode.class).getData().get("policyPeriod");
        assertFalse(strippedPeriod.has("waitingPeriodDays"), "旧键必须被链显式剥离");
        assertFalse(strippedPeriod.has("hesitationPeriodEndDate"), "旧键必须被链显式剥离");
    }

    /**
     * 生产事件存储链注册断言：{@code AxonJpaEventStoreConfig} 的 {@code .upcasterChain(...)} 调用参数
     * 必须逐项包含 {@link #UPCASTERS} 全集的注入名（括号匹配提取参数块，防注释提及冒充注册）。
     */
    @Test
    void productionEventStoreChainRegistersAllUpcasters() throws IOException {
        String source = Files.readString(Path.of(EVENT_STORE_CONFIG), StandardCharsets.UTF_8);
        String arguments = extractUpcasterChainArguments(source);

        for (EventUpcaster upcaster : UPCASTERS) {
            String beanName = Introspector.decapitalize(upcaster.getClass().getSimpleName());
            assertTrue(arguments.contains(beanName),
                    "生产注册缺少 " + beanName + "（写了 Upcaster ≠ Upcaster 生效）：" + arguments.trim());
        }
    }

    /**
     * 全量真实存量回放：dump 快照（TSV：index / payload_type / payload_revision（NULL 为字面量） / payload）
     * 逐条经「生产链 → 生产严格序列化器」，任何一条失败即列出「index + 类型 + 未知字段/异常链」清单。
     * <p>
     * 显式触发（{@code -Dm26.replay.dump=<path>}）；属性缺失时本用例不运行（非常态门禁，
     * 由 validate-m26-05.sh 保证「属性已传且文件存在」后再断言零失败）。
     * </p>
     */
    @Test
    @EnabledIfSystemProperty(named = DUMP_PROPERTY, matches = ".+")
    void replaysAllPersistedPolicyEvents() throws IOException {
        Path dump = Path.of(System.getProperty(DUMP_PROPERTY));
        assertTrue(Files.isRegularFile(dump), "dump 快照不存在：" + dump);

        List<String> failures = new ArrayList<>();
        int inspected = 0;
        for (String line : Files.readAllLines(dump, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            String[] cols = line.split("\t", 4);
            assertEquals(4, cols.length, "dump 列数异常（应 4 列），行首：" + line.substring(0, Math.min(80, line.length())));
            inspected++;
            try {
                String revision = "NULL".equals(cols[2]) ? null : cols[2];
                IntermediateEventRepresentation upcasted = upcast(cols[1], revision, cols[3]);
                SERIALIZER.deserialize(upcasted.getData(byte[].class));
            } catch (Exception ex) {
                failures.add("index=" + cols[0] + " type=" + simpleName(cols[1]) + describe(ex));
            }
        }

        assertEquals(EXPECTED_DUMP_ROWS, inspected, "dump 行数（m26-05 快照实测值；变化时先核对 dump 生成口径）");
        assertTrue(failures.isEmpty(), failures.size() + " 条存量事件在严格模式下加载失败：\n"
                + String.join("\n", failures));
    }

    /**
     * 单条事件经生产链：构造初始表示（照搬 DB 四要素：类型名 / revision / payload 字节）→ 过链 → 取首条。
     */
    private static IntermediateEventRepresentation upcast(String payloadType, String revision, String payloadJson) {
        byte[] payload = payloadJson.getBytes(StandardCharsets.UTF_8);
        GenericDomainEventEntry<byte[]> entry = new GenericDomainEventEntry<>("ReplayAggregate", "AGG-REPLAY", 0,
                "EVENT-REPLAY", Instant.parse("2026-10-16T00:00:00Z"), payloadType, revision, payload,
                "{}".getBytes(StandardCharsets.UTF_8));
        InitialEventRepresentation initial = new InitialEventRepresentation(entry, SERIALIZER);
        return CHAIN.upcast(Stream.of(initial)).findFirst().orElseThrow();
    }

    /** 括号匹配提取 {@code .upcasterChain(...)} 的参数块文本。 */
    private static String extractUpcasterChainArguments(String source) {
        int start = source.indexOf(".upcasterChain(");
        assertTrue(start >= 0, "AxonJpaEventStoreConfig 未找到 .upcasterChain( 调用");

        int open = start + ".upcasterChain(".length() - 1;
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return source.substring(open + 1, i);
                }
            }
        }
        throw new AssertionError("AxonJpaEventStoreConfig 的 .upcasterChain(...) 圆括号不闭合");
    }

    private static String simpleName(String fqcn) {
        int dot = fqcn.lastIndexOf('.');
        return dot < 0 ? fqcn : fqcn.substring(dot + 1);
    }

    private static String describe(Throwable ex) {
        StringBuilder chain = new StringBuilder();
        Throwable cursor = ex;
        for (int depth = 0; cursor != null && depth < 6; depth++) {
            if (cursor instanceof UnrecognizedPropertyException unrecognized) {
                chain.append(" unknownField=").append(unrecognized.getPropertyName());
                return chain.toString();
            }
            String message = cursor.getMessage();
            if (message != null) {
                int newline = message.indexOf('\n');
                message = newline >= 0 ? message.substring(0, newline) : message;
            }
            if (message != null && message.length() > 160) {
                message = message.substring(0, 160);
            }
            chain.append(depth == 0 ? " " : " << ").append(cursor.getClass().getSimpleName())
                    .append(": ").append(message);
            cursor = cursor.getCause();
        }
        return chain.toString();
    }
}
