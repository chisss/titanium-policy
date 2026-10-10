package com.titanium.policy.infrastructure.repository.loan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.modelling.command.Aggregate;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.axonframework.modelling.command.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.policy.aggregate.PolicyLoan;
import com.titanium.policy.event.PolicyLoanDisbursedEvent;
import com.titanium.policy.event.PolicyLoanRepaidEvent;
import com.titanium.policy.valueobject.loan.PolicyLoanAccount;

/**
 * 保单贷款账户仓储实现测试（g04-02 定义，g04-03 实现升级为事件流重建）。
 * <p>
 * 守三件事：① 经 Axon 仓储按保单ID重建事件溯源聚合（写侧强一致，不读读模型）；
 * ② 未结明细 → 领域值对象逐字段防腐转换；③ 无贷款账户/租户不符 → 空列表
 * （业务事实，非异常，不泄漏资源存在性）。
 * </p>
 */
class PolicyLoanAccountRepositoryImplTest {

    private static final String POLICY_ID = "P1";
    private static final String TENANT_ID = "T1";
    private static final LocalDateTime LOAN_DATE = LocalDateTime.of(2026, 6, 1, 10, 0);

    private Repository<PolicyLoan> policyLoanRepository;
    private PolicyLoanAccountRepositoryImpl repository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        policyLoanRepository = mock(Repository.class);
        repository = new PolicyLoanAccountRepositoryImpl(policyLoanRepository);
    }

    @Test
    void shouldRebuildAggregateFromEventStreamAndConvertOutstandingEntries() {
        stubAggregateLoad(accountWith(TENANT_ID, "L1", "10000.00", "0.060000"));

        List<PolicyLoanAccount> accounts = repository.findOutstanding(POLICY_ID, TENANT_ID);

        assertEquals(1, accounts.size());
        PolicyLoanAccount account = accounts.getFirst();
        assertEquals(0, new BigDecimal("10000.00").compareTo(account.outstandingPrincipal()));
        // 未偿利息由聚合按「放款日 → 取数时点」自然日单利 365 计提（口径在聚合，取数层不复算）
        assertTrue(account.outstandingInterest().signum() > 0);
    }

    @Test
    void shouldReturnReducedOutstandingPrincipalAfterPartialRepayment() {
        // g04-04：部分还款冲减本金后，取数契约的未偿本金须随之下降（保全域额度校验据此释放）
        PolicyLoan account = accountWith(TENANT_ID, "L1", "10000.00", "0.060000");
        account.on(new PolicyLoanRepaidEvent(
                PolicyLoan.loanAccountIdOf(POLICY_ID), POLICY_ID, "L1", "R1",
                new BigDecimal("1000.00"), new BigDecimal("195.62"), new BigDecimal("804.38"),
                new BigDecimal("195.62"), new BigDecimal("9195.62"), BigDecimal.ZERO,
                false, LOAN_DATE.plusDays(119), "op-1", TENANT_ID));
        stubAggregateLoad(account);

        List<PolicyLoanAccount> accounts = repository.findOutstanding(POLICY_ID, TENANT_ID);

        assertEquals(0, new BigDecimal("9195.62").compareTo(accounts.getFirst().outstandingPrincipal()));
    }

    @Test
    void shouldReturnEmptyListWhenAccountAbsent() {
        when(policyLoanRepository.load(PolicyLoan.loanAccountIdOf(POLICY_ID)))
                .thenThrow(new AggregateNotFoundException(POLICY_ID, "账户不存在"));

        List<PolicyLoanAccount> accounts = repository.findOutstanding(POLICY_ID, TENANT_ID);

        // 从未放款：无贷款账户即无未结贷款（业务事实），不抛异常
        assertTrue(accounts.isEmpty());
    }

    @Test
    void shouldReturnEmptyListWhenTenantMismatched() {
        stubAggregateLoad(accountWith("OTHER", "L1", "10000.00", "0.060000"));

        List<PolicyLoanAccount> accounts = repository.findOutstanding(POLICY_ID, TENANT_ID);

        // 租户不符视同不存在（调用方在保单聚合重建环节已做同口径校验）
        assertTrue(accounts.isEmpty());
    }

    @Test
    void shouldLoadThroughRealEventSourcingRepositoryOutsideCommandContext() {
        // 真机回归（g04-02 验收暴露）：查询路径无命令上下文，ES 仓储 load 要求线程内有 UnitOfWork，
        // 修复前此处抛「No UnitOfWork is currently started for this thread」（HTTP 500）。
        // 用真实 EventSourcingRepository（内存事件存储）复现：写入事件流后应重建出未结明细，
        // 而非框架级 IllegalStateException。
        EventSourcingRepository<PolicyLoan> realRepository = EventSourcingRepository.builder(PolicyLoan.class)
                .eventStore(EmbeddedEventStore.builder()
                        .storageEngine(new InMemoryEventStorageEngine())
                        .build())
                .build();
        // 先经真实仓储写入事件流（模拟历史放款；写入同样需要 UnitOfWork，成功后提交落库），
        // 随后在无命令上下文中 load 重建
        UnitOfWork<?> writeUnitOfWork = DefaultUnitOfWork.startAndGet(null);
        try {
            realRepository.newInstance(() -> new PolicyLoan())
                    .execute(account -> AggregateLifecycle.apply(new PolicyLoanDisbursedEvent(
                            PolicyLoan.loanAccountIdOf(POLICY_ID), "L1", POLICY_ID,
                            new BigDecimal("10000.00"), new BigDecimal("0.060000"),
                            LOAN_DATE, "op-1", TENANT_ID)));
            writeUnitOfWork.commit();
        } catch (Exception exception) {
            writeUnitOfWork.rollback();
            throw new AssertionError("写入事件流失败", exception);
        }
        PolicyLoanAccountRepositoryImpl realRepo = new PolicyLoanAccountRepositoryImpl(realRepository);

        List<PolicyLoanAccount> accounts = realRepo.findOutstanding(POLICY_ID, TENANT_ID);

        assertEquals(1, accounts.size());
        assertEquals(0, new BigDecimal("10000.00").compareTo(accounts.getFirst().outstandingPrincipal()));
    }

    @Test
    void shouldIsolateLoanAccountEventStreamFromForeignStream() {
        // 真机回归（g04-03 验收暴露）：修复前贷款账户复用保单ID作聚合标识，而事件存储按标识
        // 返回全部事件（不按类型过滤）——同一事件存储中标识为保单ID的他聚合事件流会混入
        // 贷款账户 load，无处理器静默跳过后聚合标识仍未落位，抛 IncompatibleAggregateException。
        // 修复后贷款账户用独立标识 LOAN:P1，两流隔离：load 只重建本流。
        EmbeddedEventStore eventStore = EmbeddedEventStore.builder()
                .storageEngine(new InMemoryEventStorageEngine())
                .build();
        EventSourcingRepository<PolicyLoan> loanRepository = EventSourcingRepository.builder(PolicyLoan.class)
                .eventStore(eventStore)
                .build();
        EventSourcingRepository<ForeignStreamAggregate> foreignRepository =
                EventSourcingRepository.builder(ForeignStreamAggregate.class)
                        .eventStore(eventStore)
                        .build();
        // 先在「保单ID」标识下写入他聚合事件流（模拟同库保单聚合的事件流）
        writeEvents(foreignRepository, () -> new ForeignStreamAggregate(),
                new ForeignStreamEvent(POLICY_ID));
        // 再写入贷款账户事件流（标识 LOAN:P1，与保单标识空间隔离）
        writeEvents(loanRepository, PolicyLoan::new,
                new PolicyLoanDisbursedEvent(
                        PolicyLoan.loanAccountIdOf(POLICY_ID), "L1", POLICY_ID,
                        new BigDecimal("10000.00"), new BigDecimal("0.060000"),
                        LOAN_DATE, "op-1", TENANT_ID));
        PolicyLoanAccountRepositoryImpl realRepo = new PolicyLoanAccountRepositoryImpl(loanRepository);

        List<PolicyLoanAccount> accounts = realRepo.findOutstanding(POLICY_ID, TENANT_ID);

        assertEquals(1, accounts.size());
        assertEquals(0, new BigDecimal("10000.00").compareTo(accounts.getFirst().outstandingPrincipal()));
    }

    /** 经真实仓储写入单条事件（写入需 UnitOfWork，成功后提交落库）。 */
    private static <T> void writeEvents(EventSourcingRepository<T> repository,
                                        java.util.concurrent.Callable<T> factory, Object event) {
        UnitOfWork<?> writeUnitOfWork = DefaultUnitOfWork.startAndGet(null);
        try {
            repository.newInstance(factory)
                    .execute(aggregate -> AggregateLifecycle.apply(event));
            writeUnitOfWork.commit();
        } catch (Exception exception) {
            writeUnitOfWork.rollback();
            throw new AssertionError("写入事件流失败", exception);
        }
    }

    /**
     * 桩聚合：仅用于模拟「同库下他聚合事件流」——标识为保单ID，与贷款账户修复前的
     * 旧标识重合（真机同库存在保单聚合事件流，本类模拟其标识空间占位）。
     */
    @org.axonframework.spring.stereotype.Aggregate
    static class ForeignStreamAggregate {
        @AggregateIdentifier
        private String foreignId;

        @EventSourcingHandler
        void on(ForeignStreamEvent event) {
            this.foreignId = event.foreignId();
        }
    }

    /** 桩聚合事件：仅承载他聚合的标识落位。 */
    record ForeignStreamEvent(String foreignId) {
    }

    /** 按事件流重建聚合（与 Axon 仓储同一路径：无参构造器 + 事件回放）。 */
    private PolicyLoan accountWith(String tenantId, String loanId, String amount, String rate) {
        PolicyLoan account = new PolicyLoan();
        account.on(new PolicyLoanDisbursedEvent(
                PolicyLoan.loanAccountIdOf(POLICY_ID), loanId, POLICY_ID,
                new BigDecimal(amount), new BigDecimal(rate), LOAN_DATE, "op-1", tenantId));
        return account;
    }

    /** 桩化 Axon 仓储 load：返回锁感知包装，invoke 提取聚合根（与取数编排层同一口径）。 */
    @SuppressWarnings("unchecked")
    private void stubAggregateLoad(PolicyLoan account) {
        Aggregate<PolicyLoan> aggregate = mock(Aggregate.class);
        when(aggregate.invoke(any())).thenAnswer(invocation -> {
            Function<PolicyLoan, Object> function = invocation.getArgument(0);
            return function.apply(account);
        });
        when(policyLoanRepository.load(PolicyLoan.loanAccountIdOf(POLICY_ID))).thenReturn(aggregate);
    }
}
