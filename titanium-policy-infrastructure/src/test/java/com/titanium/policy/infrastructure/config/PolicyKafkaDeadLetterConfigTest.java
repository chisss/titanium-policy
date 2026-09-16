package com.titanium.policy.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.DefaultErrorHandler;

/**
 * 入站消息死信配置守护测试（D-501-15）。
 * <p>
 * <b>威胁模型</b>：入站监听器一旦抛出确定性异常（业务状态不符、载荷非法），Spring Kafka 默认错误处理器
 * 会零间隔重试 10 次后<b>直接跳过</b>——位点照常提交、消费组指标健康（LAG=0），消息内容却永久丢失，
 * 现场只留一行 ERROR 日志。三个环节任一失效都会让该保护<b>静默</b>归零：
 * </p>
 * <ol>
 * <li>容器工厂未挂载自定义错误处理器 → 退回框架默认（静默丢弃）；</li>
 * <li>错误处理器未装配死信转发器 → 重试同样是空转；</li>
 * <li>死信主题未声明 → 转发目标不存在，记录被反复重投。</li>
 * </ol>
 * <p>
 * 本测试以四条判据逐一守护，且全部带<b>非空下界断言</b>——防止扫描规则本身失效后"空集合等于空集合"的假绿。
 * </p>
 */
class PolicyKafkaDeadLetterConfigTest {

    /** 监听器所在包（与 {@code KafkaConfig.INBOUND_TOPICS} 的登记范围一致） */
    private static final String MESSAGING_PACKAGE = "com.titanium.policy.infrastructure.messaging";

    /** 入站主题数量的下界：低于此值说明扫描未真正覆盖全部监听器 */
    private static final int MIN_INBOUND_TOPICS = 7;

    @Test
    @DisplayName("每个入站主题都声明了同名死信主题 Bean（否则转发目标不存在，记录被反复重投）")
    void shouldDeclareDeadLetterTopicForEveryInboundTopic() throws Exception {
        KafkaConfig config = kafkaConfig();
        // 🔴 无 KafkaAdmin 则下方 NewTopic 声明只是躺在容器里的死声明（本域未引入 spring-boot-kafka，
        // 自动配置不激活），生产环境关闭 auto-create 时转发到不存在的主题必然失败
        assertNotNull(KafkaConfig.class.getMethod("kafkaAdmin").getAnnotation(Bean.class),
                "缺少 KafkaAdmin Bean——死信主题声明不会被创建");
        Set<String> declared = new LinkedHashSet<>();

        for (Method method : KafkaConfig.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())
                    || method.getParameterCount() != 0
                    || !NewTopic.class.equals(method.getReturnType())
                    || method.getAnnotation(Bean.class) == null) {
                continue;
            }
            declared.add(((NewTopic) method.invoke(config)).name());
        }

        // 🔴 非空下界：Bean 被移除 / 反射规则失效时立即暴露，而非"空对空"通过
        assertTrue(declared.size() >= MIN_INBOUND_TOPICS,
                "死信主题 Bean 数不足，实际声明: " + declared);

        Set<String> expected = new LinkedHashSet<>();
        for (String topic : KafkaConfig.INBOUND_TOPICS) {
            expected.add(topic + KafkaConfig.DEAD_LETTER_SUFFIX);
        }
        assertEquals(expected, declared, "存在未声明死信主题的入站主题，或声明了多余的死信主题");
    }

    @Test
    @DisplayName("INBOUND_TOPICS 与实际 @KafkaListener 主题集合逐一相等（新增监听器漏登记即失败）")
    void shouldRegisterEveryListenerTopicInInboundTopics() throws Exception {
        Set<String> listenerTopics = new LinkedHashSet<>();
        int listenerCount = 0;

        // 目录枚举而非硬编码类名：新增监听器文件自动纳入守护范围
        Path messagingDir = resolveSourceDir("src/main/java/com/titanium/policy/infrastructure/messaging");
        try (Stream<Path> files = Files.list(messagingDir)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".java")).toList()) {
                String className = file.getFileName().toString().replace(".java", "");
                Class<?> listenerClass = Class.forName(MESSAGING_PACKAGE + "." + className);
                for (Method method : listenerClass.getDeclaredMethods()) {
                    KafkaListener annotation = method.getAnnotation(KafkaListener.class);
                    if (annotation == null) {
                        continue;
                    }
                    listenerCount++;
                    listenerTopics.addAll(Set.of(annotation.topics()));
                }
            }
        }

        // 🔴 非空下界：目录列举失效（路径漂移、文件被移走）时本测试必须失败而非空转
        assertTrue(listenerCount >= MIN_INBOUND_TOPICS,
                "扫描到的 @KafkaListener 数量不足，实际: " + listenerCount);

        assertEquals(new LinkedHashSet<>(KafkaConfig.INBOUND_TOPICS), listenerTopics,
                "INBOUND_TOPICS 与实际监听器主题不一致——漏登记的主题在重试耗尽后无死信出路");
    }

    @Test
    @DisplayName("监听容器工厂已挂载自定义错误处理器（未挂载即退回框架默认的静默丢弃）")
    void shouldAttachCustomErrorHandlerToListenerContainerFactory() {
        KafkaConfig config = kafkaConfig();
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                (ConcurrentKafkaListenerContainerFactory<String, String>) config.kafkaListenerContainerFactory();
        ConcurrentMessageListenerContainer<String, String> container = factory.createContainer("dlq-probe-topic");

        // 未调用 setCommonErrorHandler 时该值为 null（框架不会在裸工厂上补默认值）
        assertNotNull(container.getCommonErrorHandler(), "容器工厂未挂载自定义错误处理器");
        assertInstanceOf(DefaultErrorHandler.class, container.getCommonErrorHandler());
    }

    @Test
    @DisplayName("错误处理器装配了死信转发器且重试有界（源码级接线断言，框架未暴露读取口）")
    void shouldWireDeadLetterRecovererIntoErrorHandler() throws Exception {
        Path source = resolveSourceDir("src/main/java/com/titanium/policy/infrastructure/config")
                .resolve("KafkaConfig.java");
        assertTrue(Files.isRegularFile(source), "KafkaConfig 源码不存在: " + source);
        String code = Files.readString(source, StandardCharsets.UTF_8);

        // 🔴 为何用源码断言：DefaultErrorHandler/FailedRecordProcessor 均未对外暴露 recoverer 读取口，
        // 无法从容器或 Bean 实例反查转发器，故此处以接线语句锁定——删除任一行即失败。
        assertTrue(code.contains("new DeadLetterPublishingRecoverer("),
                "错误处理器未装配死信转发器——重试耗尽仍是静默丢弃");
        assertTrue(code.contains("record.topic() + DEAD_LETTER_SUFFIX"),
                "死信转发目标未按「原主题 + -dlt」路由");
        assertTrue(code.contains("new FixedBackOff("),
                "重试未使用有界退避——零间隔重试对确定性失败毫无意义");
        assertTrue(code.contains("factory.setCommonErrorHandler("),
                "容器工厂未接线错误处理器");
    }

    // ==================== 夹具 ====================

    /**
     * 构造配置实例并补齐 {@code @Value} 注入项。
     * <p>
     * 副本因子无 Spring 容器时为 0，而 {@code TopicBuilder} 要求 ≥1，故按单 broker 部署值注入；
     * 引导地址/消费组为 {@code DefaultKafkaConsumerFactory} 的必填项（null 会在其构造器内 NPE），
     * 此处给占位值——本测试不建立任何 broker 连接。
     * </p>
     */
    private KafkaConfig kafkaConfig() {
        try {
            KafkaConfig config = new KafkaConfig();
            setField(config, "topicReplicationFactor", 1);
            setField(config, "bootstrapServers", "localhost:9092");
            setField(config, "groupId", "policy-group");
            return config;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("构造 KafkaConfig 失败", e);
        }
    }

    private void setField(KafkaConfig config, String name, Object value) throws ReflectiveOperationException {
        Field field = KafkaConfig.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(config, value);
    }

    /** 自当前工作目录逐级上溯定位源码目录（maven 从模块目录或仓库根启动均可） */
    private Path resolveSourceDir(String relative) {
        Path current = Paths.get("").toAbsolutePath();
        for (int depth = 0; depth < 6 && current != null; depth++) {
            Path candidate = current.resolve(relative);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("未能定位源码目录: " + relative + "，当前目录: " + Paths.get("").toAbsolutePath());
    }
}
