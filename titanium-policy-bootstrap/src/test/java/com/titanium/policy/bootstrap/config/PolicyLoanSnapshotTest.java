package com.titanium.policy.bootstrap.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.AggregateSnapshotter;
import org.axonframework.eventsourcing.EventCountSnapshotTriggerDefinition;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.GenericAggregateFactory;
import org.axonframework.eventsourcing.SnapshotTriggerDefinition;
import org.axonframework.eventsourcing.Snapshotter;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.modelling.command.AggregateAnnotationCommandHandler;
import org.axonframework.modelling.command.Repository;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.spring.stereotype.Aggregate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.titanium.policy.aggregate.Insurance;
import com.titanium.policy.aggregate.Policy;
import com.titanium.policy.aggregate.PolicyLoan;
import com.titanium.policy.aggregate.Proposal;
import com.titanium.policy.command.DisbursePolicyLoanCommand;
import com.titanium.policy.command.RepayPolicyLoanCommand;

/**
 * 保单贷款账户快照机制测试（m26-04 / AXN-03：注解装配路径的往返验收）。
 * <p>
 * 全域快照触发器有两条装配路径——显式 {@code Repository} Bean 的 {@code builder.snapshotTriggerDefinition(...)}
 * 与纯自动装配的 {@code @Aggregate(snapshotTriggerDefinition = "beanName")} 注解引用。维护域样板
 * （{@code MaintenanceSnapshotTest}）覆盖前者；本测试覆盖<b>后者</b>，取 policy 域事件数第二高的聚合
 * PolicyLoan（32 条，m26-04 真机实测）作主体。
 * </p>
 * <p>
 * 覆盖四层判据：
 * <ol>
 * <li><b>接线</b>：聚合根注解引用的 Bean 名的<b>真值集合</b>必须与生产配置（{@link AxonConfig} 经 Spring 容器
 * 实例化后的 Bean 定义）声明名的<b>真值集合</b>恰好相等——两侧各取真值互比，不对照写死清单（写死清单既会把
 * 「两侧一致改动」判红，也证明不了出货物里真有其物）；并按 Axon 的解析方式 {@code getBean(name, type)}
 * 真解析一次（防 {@code containsBean} 式软判据）；阈值独立断言为生产值 50；</li>
 * <li><b>机制</b>：真实命令流跨过阈值后，事件存储中必须出现快照（载荷为聚合状态实例），且加载之后存储中的
 * 快照内容仍保持在快照时刻；</li>
 * <li><b>消费对照</b>：带快照的加载实际消费的非快照事件数必须严格小于纯回放加载（计数尺子由纯回放侧自证：
 * 恰好等于事件总数）——若快照未被加载路径使用，本判据判红；</li>
 * <li><b>往返一致</b>：快照恢复状态、序列化再还原状态、纯回放状态三者的序列化视图必须逐字段一致，且明细集合
 * 与其<b>嵌套还款记录</b>都必须整体进入视图（防「空壳聚合」与成员集合静默丢失两类假绿）。</li>
 * </ol>
 * 🔴 <b>存储语义口径（判据成立的前提，勿删）</b>：Axon 恢复聚合时，若事件流首条是快照，则<b>直接以快照载荷
 * （聚合状态对象）作为聚合根</b>（{@code AbstractAggregateFactory.fromSnapshot}）。生产 JPA 引擎每次读取都把存储
 * 字节反序列化为新对象，加载拿到的是副本；而 InMemory 引擎按对象引用存储，原样返回会让加载把存储中的同一对象
 * 直接当聚合根——后续命令在它之上叠加事件，下一次加载又把增量事件重复应用到它之上，读数逐轮复利膨胀。
 * 故此处包装事件存储：读取流中的聚合状态载荷用 {@link JacksonSerializer} 深复制（模拟生产的反序列化边界），
 * 其余消息原样透传并计数。
 * </p>
 * <p>
 * 🔴 <b>序列化器取生产口径</b>：快照往返用 {@link AxonSerializerConfig#buildObjectMapper()}（m26-05 建立的
 * 「生产装配与测试共用同一 ObjectMapper」锚点），而非 {@code JacksonSerializer.defaultSerializer()}——后者
 * 宽松且不带 JSR-310 口径，用它测出的往返一致不能代表生产可加载。
 * </p>
 */
class PolicyLoanSnapshotTest {

    /** 生产快照阈值（独立重声明，不引用 AxonConfig 私有常量——判据与实现不共用同一来源） */
    private static final int SNAPSHOT_THRESHOLD_PRODUCTION = 50;

    private static final String POLICY_ID = "policy-snapshot-probe";
    private static final String TENANT_ID = "tenant-snapshot-probe";
    private static final String OPERATOR  = "snapshot-probe";
    private static final LocalDateTime LOAN_DATE = LocalDateTime.of(2026, 6, 1, 9, 0);

    private static final BigDecimal DISBURSE_AMOUNT = new BigDecimal("10000.00");
    private static final BigDecimal INTEREST_RATE   = new BigDecimal("0.05");
    /** 还款 100.00 元：远小于欠款总额（本金 10000 + 1 日利息 ≈ 1.37），走部分还款而非结清 */
    private static final BigDecimal REPAY_AMOUNT    = new BigDecimal("100.00");

    /** 快照触发前的放款笔数（第 49 个事件） */
    private static final int DISBURSE_BEFORE_REPAY = 49;
    /** 快照触发后的放款笔数（2 笔增量事件） */
    private static final int DISBURSE_AFTER_REPAY  = 2;
    /** 1..49 放款 + 第 50 个事件还款 + 50/51 两笔追加放款 */
    private static final int TOTAL_EVENTS = DISBURSE_BEFORE_REPAY + 1 + DISBURSE_AFTER_REPAY;

    /** 阈值 50 在第 50 个事件（还款）提交时触发 ⇒ 快照应含 49 条明细（「快照时刻」基准） */
    private static final int SNAPSHOT_ENTRY_COUNT = DISBURSE_BEFORE_REPAY;
    /** 全量回放后明细数 = 快照时刻 49 + 2 笔增量 */
    private static final int REPLAYED_ENTRY_COUNT = DISBURSE_BEFORE_REPAY + DISBURSE_AFTER_REPAY;

    /**
     * 序列化视图字段数下界（防「空壳」假绿：若序列化器看不见私有字段，输出为空对象，比较必然「相等」）。
     * PolicyLoan 顶层字段恰为 4（loanAccountId / policyId / tenantId / entries），任何键静默丢失都会跌破下界。
     */
    private static final int SERIALIZED_FIELD_FLOOR = 4;

    @Test
    @DisplayName("接线：注解装配路径——聚合根注解引用的 Bean 名必须与生产配置声明名恰好一致且可解析，阈值 50")
    void shouldWireSnapshotTriggerViaAggregateAnnotation() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(Snapshotter.class, () -> (type, identifier) -> {
            });
            context.register(AxonConfig.class);
            context.refresh();

            // 侧 1：生产 Bean 定义的真值（本域四个聚合共用单一触发器，注解引用名应收敛为单元素集）
            Set<String> referencedNames = Stream.of(Policy.class, PolicyLoan.class, Insurance.class, Proposal.class)
                    .map(type -> type.getAnnotation(Aggregate.class).snapshotTriggerDefinition())
                    .collect(Collectors.toSet());
            assertEquals(1, referencedNames.size(),
                    "本域四个聚合应共用同一个快照触发器 Bean，实测引用名=" + referencedNames);
            String referenced = referencedNames.iterator().next();

            // 侧 2：Spring 容器中实际声明的触发器 Bean 名
            Set<String> declaredNames = Set.of(context.getBeanNamesForType(SnapshotTriggerDefinition.class));

            // 判据：两侧真值集合恰好相等——注解引用了不存在的 Bean（启动期 NoSuchBeanDefinitionException）
            // 与声明了无人引用的孤儿触发器，两个方向都必须判红
            assertEquals(referencedNames, declaredNames,
                    "聚合根注解引用的触发器 Bean 名必须与生产配置声明的恰好一致");

            // 按 Axon 的解析方式真解析一次（getBean(name, type)，非 containsBean 式软判据）
            SnapshotTriggerDefinition resolved = context.getBean(referenced, SnapshotTriggerDefinition.class);

            Field thresholdField = EventCountSnapshotTriggerDefinition.class.getDeclaredField("threshold");
            thresholdField.setAccessible(true);
            assertEquals(SNAPSHOT_THRESHOLD_PRODUCTION, thresholdField.getInt(resolved),
                    "生产阈值须为 50（2026-10-16 实测：本域单聚合事件数最多 32、达 50 的为 0）");
        }
    }

    @Test
    @DisplayName("机制+往返：跨阈值产生快照且被加载消费，快照/序列化还原/纯回放三者状态一致")
    void snapshotRoundTripShouldMatchEventReplay() throws Exception {
        InMemoryEventStorageEngine engineA = new InMemoryEventStorageEngine();
        EmbeddedEventStore storeA = EmbeddedEventStore.builder().storageEngine(engineA).build();
        InMemoryEventStorageEngine engineB = new InMemoryEventStorageEngine();
        EmbeddedEventStore storeB = EmbeddedEventStore.builder().storageEngine(engineB).build();
        try {
            JacksonSerializer serializer = JacksonSerializer.builder()
                    .objectMapper(AxonSerializerConfig.buildObjectMapper())
                    .build();
            AtomicInteger consumedOnA = new AtomicInteger();
            AtomicInteger consumedOnB = new AtomicInteger();
            EventStore storeForA = deserializingCountingEventStore(storeA, consumedOnA, serializer);
            EventStore storeForB = deserializingCountingEventStore(storeB, consumedOnB, serializer);

            AggregateSnapshotter snapshotter = AggregateSnapshotter.builder()
                    .eventStore(storeForA)
                    .executor(Runnable::run)
                    .aggregateFactories(new GenericAggregateFactory<>(PolicyLoan.class))
                    .build();

            // A 路径：触发器取自生产配置（AxonConfig 经 Spring 实例化的那个 Bean），仅把 Snapshotter 换成本测试
            // 的实例——机制判据与接线的解析链同源贯通，不手工复刻触发器
            SnapshotTriggerDefinition triggerA = productionTrigger(snapshotter);
            Repository<PolicyLoan> repositoryA = EventSourcingRepository.builder(PolicyLoan.class)
                    .eventStore(storeForA)
                    .snapshotTriggerDefinition(triggerA)
                    .build();
            // B 路径：无快照触发器的纯回放对照（事件从 A 复制，保证载荷逐条相同）
            Repository<PolicyLoan> repositoryB = EventSourcingRepository.builder(PolicyLoan.class)
                    .eventStore(storeForB)
                    .build();

            runCommandFlow(repositoryA);
            String aggregateId = PolicyLoan.loanAccountIdOf(POLICY_ID);

            // —— 判据 2：跨过阈值后快照确实产生。Axon 4.10 的消息接口无 isSnapshot 标记，
            // 快照识别判据 = payload 为聚合实例（普通事件的 payload 恒为事件类，二者无交集）——
            Optional<DomainEventMessage<?>> snapshot = engineA.readSnapshot(aggregateId);
            assertTrue(snapshot.isPresent(),
                    "事件数(" + TOTAL_EVENTS + ")跨过阈值(" + SNAPSHOT_THRESHOLD_PRODUCTION + ")后必须产生快照");
            assertTrue(snapshot.get().getPayload() instanceof PolicyLoan,
                    "快照载荷必须是聚合状态实例（而非事件类）");
            assertTrue(storeA.readEvents(aggregateId).peek().getPayload() instanceof PolicyLoan,
                    "事件流头部应是快照（加载路径据此跳过快照覆盖的事件段）");

            // —— 复制全量事件到对照引擎。🔴 必须用引擎层直读：EventStore 层的 readEvents 会把最新快照
            // 拼在流头，引擎层 readEvents 才是纯事件（无快照拼接）——
            List<DomainEventMessage<?>> replayedEvents = new ArrayList<>();
            engineA.readEvents(aggregateId).asStream().forEach(replayedEvents::add);
            assertEquals(TOTAL_EVENTS, replayedEvents.size(), "对照引擎应恰好收到全部回放事件（不含快照）");
            engineB.appendEvents(replayedEvents);

            // —— 判据 3：消费计数对照（证明带快照的加载确实跳过了快照覆盖段）——
            consumedOnA.set(0);
            PolicyLoan stateA = loadState(repositoryA, aggregateId);
            consumedOnB.set(0);
            PolicyLoan stateB = loadState(repositoryB, aggregateId);
            assertEquals(TOTAL_EVENTS, consumedOnB.get(), "纯回放必须消费全部事件（计数尺子有效性自证）");
            assertTrue(consumedOnA.get() < consumedOnB.get(),
                    "带快照的加载须跳过被快照覆盖的事件段，实测 A=" + consumedOnA.get() + " B=" + consumedOnB.get());

            // —— 判据 2（续）：存储中的快照必须仍处于「快照时刻」形态 ——
            // 若加载路径拿到的是存储对象本身（InMemory 引用语义），命令会在它之上叠加明细，此处读数必然大于 49；
            // 该断言即「反序列化边界」包装的哨兵，也等价于生产 JPA 引擎「读取即反序列化为新对象」的行为。
            assertEquals(SNAPSHOT_ENTRY_COUNT,
                    ((PolicyLoan) engineA.readSnapshot(aggregateId).orElseThrow().getPayload()).getEntries().size(),
                    "加载/命令处理不允许原地改写存储中的快照载荷");

            // —— 判据 4：序列化往返与回放一致（含防「空壳」下界与成员集合形态断言）——
            ObjectMapper mapper = new ObjectMapper();
            SerializedObject<byte[]> serializedB = serializer.serialize(stateB, byte[].class);
            JsonNode treeB = mapper.readTree(serializedB.getData());
            JsonNode treeA = mapper.readTree(serializer.serialize(stateA, byte[].class).getData());
            Object restored = serializer.deserialize(serializedB);
            JsonNode treeRestored = mapper.readTree(serializer.serialize(restored, byte[].class).getData());

            assertTrue(treeB.size() >= SERIALIZED_FIELD_FLOOR,
                    "序列化视图必须覆盖聚合字段（防空壳假绿），实测字段数=" + treeB.size());
            // 成员集合防护：明细列表必须整体进入序列化视图且条数等于事件流累计（root 侧成员字段无 getter 时静默丢失、不抛异常）
            assertEquals(REPLAYED_ENTRY_COUNT, treeB.path("entries").size(),
                    "贷款明细列表必须整体进入序列化视图（缺失/空壳说明 root 侧成员字段未进 JSON）");
            // 嵌套集合防护：首笔明细的还款记录必须进视图——PolicyLoanEntry 是聚合内嵌套 record，
            // 其 repayments 丢失会静默产出「无还款历史」的快照，恢复后利息与本金校准全部失真
            assertEquals(1, treeB.path("entries").get(0).path("repayments").size(),
                    "嵌套还款记录必须进入序列化视图（丢失 ⇒ 快照恢复后还款历史与未偿余额失真）");
            assertEquals(treeB, treeA, "快照恢复状态与事件回放状态必须逐字段一致");
            assertEquals(treeB, treeRestored, "序列化再还原必须与事件回放结果一致");
        } finally {
            storeA.shutDown();
            storeB.shutDown();
        }
    }

    /**
     * 取生产配置装配出的快照触发器（AxonConfig 经 Spring 实例化的那个 Bean），仅把 Snapshotter 替换为测试实例。
     * <p>
     * 解析方式与 Axon 的 Spring 集成一致（{@code getBean(注解引用名, 类型)}），故本方法同时是「注解引用名可解析」
     * 在机制侧的复用——若注解引用的 Bean 名与生产声明不符，此处直接抛 {@code NoSuchBeanDefinitionException}。
     * </p>
     */
    private static SnapshotTriggerDefinition productionTrigger(Snapshotter snapshotter) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(Snapshotter.class, () -> snapshotter);
            context.register(AxonConfig.class);
            context.refresh();
            String referenced = PolicyLoan.class.getAnnotation(Aggregate.class).snapshotTriggerDefinition();
            return context.getBean(referenced, SnapshotTriggerDefinition.class);
        }
    }

    /**
     * 在临时 UnitOfWork 中加载聚合状态（{@code repository.load} 要求当前线程存在 UoW； 生产路径由命令处理器建立，测试直载须自备）。
     */
    private static PolicyLoan loadState(Repository<PolicyLoan> repository, String aggregateId) {
        UnitOfWork<?> unitOfWork = DefaultUnitOfWork.startAndGet(GenericMessage.asMessage("snapshot-load-probe"));
        try {
            PolicyLoan state = repository.load(aggregateId).invoke(root -> root);
            unitOfWork.commit();
            return state;
        } catch (RuntimeException e) {
            unitOfWork.rollback(e);
            throw e;
        }
    }

    /**
     * 通过命令网关跑完整命令流：49 笔放款 → 对首笔放款还款（第 50 个事件，触发快照）→ 2 笔追加放款。
     * <p>
     * 还款置于阈值事件上，使快照同时覆盖<b>两种事件类型</b>与<b>嵌套还款记录</b>两种形态——比纯同类型事件流
     * 更能暴露「某类处理器未参与状态重建」的缺陷。首笔放款经 {@code CREATE_IF_MISSING} 建账户，其余为追加。
     * </p>
     */
    private static void runCommandFlow(Repository<PolicyLoan> repository) {
        SimpleCommandBus commandBus = SimpleCommandBus.builder().build();
        AggregateAnnotationCommandHandler.<PolicyLoan>builder()
                .repository(repository)
                .aggregateType(PolicyLoan.class)
                .build()
                .subscribe(commandBus);
        DefaultCommandGateway gateway = DefaultCommandGateway.builder().commandBus(commandBus).build();

        for (int i = 1; i <= DISBURSE_BEFORE_REPAY; i++) {
            gateway.sendAndWait(DisbursePolicyLoanCommand.of(TENANT_ID, POLICY_ID, loanId(i),
                    DISBURSE_AMOUNT, INTEREST_RATE, LOAN_DATE, OPERATOR));
        }
        gateway.sendAndWait(RepayPolicyLoanCommand.of(TENANT_ID, POLICY_ID, loanId(1), "REPAY-0001",
                REPAY_AMOUNT, LOAN_DATE.plusDays(1), OPERATOR));
        for (int i = DISBURSE_BEFORE_REPAY + 1; i <= REPLAYED_ENTRY_COUNT; i++) {
            gateway.sendAndWait(DisbursePolicyLoanCommand.of(TENANT_ID, POLICY_ID, loanId(i),
                    DISBURSE_AMOUNT, INTEREST_RATE, LOAN_DATE, OPERATOR));
        }
    }

    private static String loanId(int ordinal) {
        return String.format("LOAN-%04d", ordinal);
    }

    /**
     * 包装事件存储，模拟生产 JPA 引擎的读取语义并统计消费数： 读取流中的聚合状态载荷（快照）逐次深复制（生产每次读取都反序列化出新对象）， 其余消息原样透传；同时统计实际消费的<b>非快照</b>消息数——用于判据「带快照的加载是否真的跳过了快照覆盖的事件段」。
     */
    private static EventStore deserializingCountingEventStore(EventStore delegate, AtomicInteger nonSnapshotConsumed,
            JacksonSerializer serializer) {
        return (EventStore) Proxy.newProxyInstance(EventStore.class.getClassLoader(), new Class<?>[] { EventStore.class },
                (proxy, method, args) -> {
                    Object result = method.invoke(delegate, args);
                    if (result instanceof DomainEventStream stream) {
                        return deserializingCountingStream(stream, nonSnapshotConsumed, serializer);
                    }
                    return result;
                });
    }

    private static DomainEventStream deserializingCountingStream(DomainEventStream delegate,
            AtomicInteger nonSnapshotConsumed, JacksonSerializer serializer) {
        return new DomainEventStream() {
            @Override
            public boolean hasNext() {
                return delegate.hasNext();
            }

            @Override
            public DomainEventMessage<?> next() {
                DomainEventMessage<?> message = delegate.next();
                // 聚合类载荷即快照（普通事件 payload 恒为事件类，二者无交集）——复制以模拟生产的反序列化边界
                if (message.getPayload() instanceof PolicyLoan aggregate) {
                    return deserializeCopy(message, aggregate, serializer);
                }
                nonSnapshotConsumed.incrementAndGet();
                return message;
            }

            @Override
            public DomainEventMessage<?> peek() {
                // 🔴 peek 必须同样过复制边界：仓库以流首（peek）为聚合根种子恢复快照聚合
                // （AbstractAggregateFactory.fromSnapshot），只包装 next() 会让加载拿到存储对象本身。
                return copyAggregatePayload(delegate.peek());
            }

            @Override
            public Long getLastSequenceNumber() {
                return delegate.getLastSequenceNumber();
            }

            private DomainEventMessage<?> copyAggregatePayload(DomainEventMessage<?> message) {
                if (message != null && message.getPayload() instanceof PolicyLoan aggregate) {
                    return deserializeCopy(message, aggregate, serializer);
                }
                return message;
            }
        };
    }

    /** 经 Jackson 序列化往返复制聚合状态，重建等序号的领域事件消息（快照消息的其余分量逐项保留）。 */
    private static DomainEventMessage<?> deserializeCopy(DomainEventMessage<?> message, PolicyLoan aggregate,
            JacksonSerializer serializer) {
        SerializedObject<byte[]> serialized = serializer.serialize(aggregate, byte[].class);
        PolicyLoan copy = serializer.deserialize(new SimpleSerializedObject<>(serialized.getData(), byte[].class,
                PolicyLoan.class.getName(), null));
        return new GenericDomainEventMessage<>(message.getType(), message.getAggregateIdentifier(),
                message.getSequenceNumber(), copy, message.getMetaData(), message.getIdentifier(), message.getTimestamp());
    }
}
