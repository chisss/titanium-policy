package com.titanium.policy.infrastructure.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import com.titanium.metadata.topic.CrossDomainTopics;

/**
 * Kafka配置类
 * <p>
 * Spring Boot 4.0 不再自动注册 @KafkaListener 注解后处理器，须显式 @EnableKafka 激活
 * （同 maintenance/customer 域先例），否则跨域入站监听器（身故给付结算等）静默不消费。
 * </p>
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    /**
     * 主题副本因子：**部署环境属性**，不是代码常量。
     * <p>🔴 D-501-27：硬编码 {@code replicas(2)} 在单 broker 环境下会让 {@code KafkaAdmin} 创建主题
     * 直接失败（{@code InvalidReplicationFactorException}）。现默认 1（单 broker 开箱可用），
     * 多 broker 环境经配置覆盖为 3。</p>
     */
    @Value("${kafka.topic.replication-factor:1}")
    private int    topicReplicationFactor;

    /**
     * 死信主题后缀。
     * <p>与 {@code DeadLetterPublishingRecoverer} 的默认后缀一致：死信主题名 = 原主题名 + 本后缀。</p>
     */
    static final String DEAD_LETTER_SUFFIX = "-dlt";

    /** 死信主题分区数：与生产者默认分区数一致，避免转发时按分区号路由越界 */
    static final int    DEAD_LETTER_PARTITIONS = 3;

    /** 入站消息重试间隔（毫秒）：零间隔重试对确定性失败毫无意义，见 {@code kafkaDefaultErrorHandler} */
    static final long   RETRY_INTERVAL_MILLIS  = 2000L;

    /** 入站消息最大重试次数：耗尽即转死信主题 */
    static final long   RETRY_MAX_ATTEMPTS     = 3L;

    /**
     * 本域<b>全部入站主题</b>（与各 {@code @KafkaListener} 一一对应）——死信主题声明的单一事实来源。
     * <p>
     * 🔴 新增入站监听器时必须在此登记其主题，否则该类消息重试耗尽后无死信出路
     * （{@code DeadLetterPublishingRecoverer} 发往不存在的主题会失败，记录将被反复重投而非归档）。
     * 该一致性由 {@code PolicyKafkaDeadLetterConfigTest} 机械守护：它会逐一比对
     * {@code messaging} 包内 {@code @KafkaListener} 的主题与本清单，不一致即测试失败。
     * </p>
     */
    static final List<String> INBOUND_TOPICS = List.of(
            CrossDomainTopics.UNDERWRITING_DECIDED,
            CrossDomainTopics.MAINTENANCE_EXECUTED,
            CrossDomainTopics.BILLING_LAPSE_NOTIFICATION,
            CrossDomainTopics.CLAIM_DEATH_BENEFIT_SETTLED,
            CrossDomainTopics.CLAIM_DISABILITY_BENEFIT_SETTLED,
            CrossDomainTopics.PAYMENT_ORDER_PAID,
            CrossDomainTopics.PAYMENT_ORDER_FAILED);

    /**
     * 生产者配置。
     *
     * <p>🔴 <b>可靠性参数必须在此显式声明</b>：本仓未引入 {@code spring-boot-kafka}（{@code KafkaProperties}
     * 所在模块），Boot 的 Kafka 自动配置不激活，yml 里的 {@code spring.kafka.producer.*} <b>没有任何消费者</b>
     * ——写了也不生效，读配置的人却会以为已配好。故生产者参数以本方法为唯一事实来源。</p>
     *
     * <ul>
     *   <li>{@code acks=all}：leader 需等全部同步副本确认，防 leader 切换时丢消息（Kafka 默认 {@code acks=1}
     *       只等 leader 本地写入，leader 随即崩溃即丢）；</li>
     *   <li>{@code enable.idempotence=true}：生产者幂等，重试不会产生重复消息（配合 acks=all 才可开启）；</li>
     *   <li>{@code retries}：瞬时故障（网络抖动、leader 选举）自动重试，避免直接落入死信队列。</li>
     * </ul>
     */
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Kafka模板
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * 消费者配置
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Kafka监听器容器工厂
     */
    @Bean
    public KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, String>> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        // 🔴 必须显式挂载：本仓未引入 spring-boot-kafka，Boot 不会把容器里的 CommonErrorHandler
        // 自动接到容器工厂上。不挂载即退回 Spring Kafka 默认（10 次零间隔重试后丢弃），
        // 正是 D-501-15 中「重试耗尽消息被静默丢弃」的成因。
        factory.setCommonErrorHandler(kafkaDefaultErrorHandler(kafkaTemplate()));
        return factory;
    }

    /**
     * 入站消息的终态处理：有限次退避重试后<b>转发到死信主题</b>，而非直接丢弃。
     * <p>
     * 🔴 <b>D-501-15</b>：修复前用 Spring Kafka 默认处理器（10 次重试、间隔 0、无死信转发）。
     * 零间隔重试对<b>确定性失败</b>（业务状态不符、载荷非法）毫无意义——状态不会因重试而改变，
     * 10 次空转后位点照常提交，消息<b>内容永久丢失</b>，消费组指标却完全健康（LAG=0）。
     * 现改为「3 次 × 2 秒」有界退避后转死信：
     * </p>
     * <ul>
     *   <li>退避留出时间窗，覆盖数据库连接闪断、下游短暂不可用等<b>瞬时</b>故障；</li>
     *   <li>耗尽后 {@link DeadLetterPublishingRecoverer} 把原始报文投到 {@code <原主题>-dlt}，
     *       消息<b>可检索、可重放</b>，不再是「只剩一行 ERROR 日志」。</li>
     * </ul>
     * <p>
     * ⚠️ <b>死信主题必须已存在</b>：转发目标不存在时发布失败，记录会被反复重投。
     * 故本类同时声明全部入站主题的死信主题（见 {@link #INBOUND_TOPICS} 与下方 {@code *DeadLetterTopic} Bean），
     * 并由 {@code PolicyKafkaDeadLetterConfigTest} 机械守护「监听器主题 ↔ 死信主题」的一致性。
     * </p>
     *
     * @param kafkaTemplate 生产者模板（死信转发的投递通道）
     * @return 入站消息终态处理器
     */
    @Bean
    public DefaultErrorHandler kafkaDefaultErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        // 分区传 -1 表示交由 Kafka 默认分区器选择，避免源主题分区数多于死信主题时报分区越界
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + DEAD_LETTER_SUFFIX, -1));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MILLIS, RETRY_MAX_ATTEMPTS));
    }

    /**
     * Kafka Admin：消费本类声明的 {@link NewTopic} Bean 并在 broker 上创建主题。
     * <p>🔴 D-501-27：本域依赖裸 {@code spring-kafka}，Boot 的 Kafka 自动配置不激活，
     * {@code KafkaAdmin} 不会自动注册——缺了它，下方死信主题声明只是躺在容器里的死声明，
     * 生产环境（关闭 auto-create）转发到不存在的主题必然失败。对齐 billing/claim/payment/regulatory 样板。</p>
     *
     * @return Kafka Admin
     */
    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    // ==================== 死信主题声明（每个入站主题一个） ====================

    @Bean
    public NewTopic underwritingDecidedDeadLetterTopic() {
        return deadLetterTopic(CrossDomainTopics.UNDERWRITING_DECIDED);
    }

    @Bean
    public NewTopic maintenanceExecutedDeadLetterTopic() {
        return deadLetterTopic(CrossDomainTopics.MAINTENANCE_EXECUTED);
    }

    @Bean
    public NewTopic billingLapseNotificationDeadLetterTopic() {
        return deadLetterTopic(CrossDomainTopics.BILLING_LAPSE_NOTIFICATION);
    }

    @Bean
    public NewTopic claimDeathBenefitSettledDeadLetterTopic() {
        return deadLetterTopic(CrossDomainTopics.CLAIM_DEATH_BENEFIT_SETTLED);
    }

    @Bean
    public NewTopic claimDisabilityBenefitSettledDeadLetterTopic() {
        return deadLetterTopic(CrossDomainTopics.CLAIM_DISABILITY_BENEFIT_SETTLED);
    }

    @Bean
    public NewTopic paymentOrderPaidDeadLetterTopic() {
        return deadLetterTopic(CrossDomainTopics.PAYMENT_ORDER_PAID);
    }

    @Bean
    public NewTopic paymentOrderFailedDeadLetterTopic() {
        return deadLetterTopic(CrossDomainTopics.PAYMENT_ORDER_FAILED);
    }

    /** 死信主题统一参数：命名 = 原主题 + {@link #DEAD_LETTER_SUFFIX}，副本因子取部署配置 */
    private NewTopic deadLetterTopic(String sourceTopic) {
        return TopicBuilder.name(sourceTopic + DEAD_LETTER_SUFFIX)
                .partitions(DEAD_LETTER_PARTITIONS)
                .replicas(topicReplicationFactor)
                .build();
    }
}
