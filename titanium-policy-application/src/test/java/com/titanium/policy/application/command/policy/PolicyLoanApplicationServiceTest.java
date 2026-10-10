package com.titanium.policy.application.command.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.modelling.command.Aggregate;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.axonframework.modelling.command.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.exception.DomainException;
import com.titanium.policy.aggregate.Policy;
import com.titanium.policy.command.DisbursePolicyLoanCommand;
import com.titanium.policy.common.enums.PolicyStatusCode;
import com.titanium.policy.entity.loan.PolicyLoanEntry;
import com.titanium.policy.valueobject.policy.PolicyStatus;

/**
 * 保单贷款写侧应用门面测试（g04-03 真机回归补充）。
 * <p>
 * 守四件事：① 保单生效才派发放款命令；② 未生效/不存在/租户不符一律业务码拒绝且不派发；
 * ③ 经 Axon 仓储重建保单（写侧强一致）；④ 查询路径无命令上下文时仓储 load 仍可用
 * （真机回归：修复前抛「No UnitOfWork is currently started for this thread」）。
 * </p>
 */
class PolicyLoanApplicationServiceTest {

    private static final String TENANT_ID = "T1";
    private static final String POLICY_ID = "P1";

    private final CommandGateway commandGateway = mock(CommandGateway.class);
    private final Repository<Policy> policyRepository = mock(Repository.class);

    private PolicyLoanApplicationService service;

    @BeforeEach
    void setUp() {
        service = new PolicyLoanApplicationService(commandGateway, policyRepository);
    }

    @Test
    void shouldDispatchCommandWhenPolicyEffective() {
        stubAggregateLoad(policyWith(PolicyStatusCode.EFFECTIVE));
        PolicyLoanEntry entry = new PolicyLoanEntry("L1", new BigDecimal("1000.00"),
                new BigDecimal("0.050000"), LocalDateTime.now(), List.of());
        when(commandGateway.sendAndWait(any(DisbursePolicyLoanCommand.class))).thenReturn(entry);

        PolicyLoanEntry result = service.disburse(command());

        assertEquals("L1", result.loanId());
        verify(commandGateway).sendAndWait(any(DisbursePolicyLoanCommand.class));
    }

    @Test
    void shouldRejectWhenPolicyNotEffective() {
        stubAggregateLoad(policyWith(PolicyStatusCode.NOT_EFFECTIVE));

        DomainException exception = assertThrows(DomainException.class, () -> service.disburse(command()));

        assertEquals(PolicyErrorCode.POLICY_NOT_EFFECTIVE, exception.getErrorCode());
        verifyNoInteractions(commandGateway);
    }

    @Test
    void shouldRejectWhenPolicyMissing() {
        when(policyRepository.load(POLICY_ID))
                .thenThrow(new AggregateNotFoundException(POLICY_ID, "聚合不存在"));

        DomainException exception = assertThrows(DomainException.class, () -> service.disburse(command()));

        assertEquals(PolicyErrorCode.POLICY_NOT_EXIST, exception.getErrorCode());
        verifyNoInteractions(commandGateway);
    }

    @Test
    void shouldRejectWhenTenantMismatch() {
        stubAggregateLoad(policyWith(PolicyStatusCode.EFFECTIVE).toBuilder().tenantId("OTHER").build());

        DomainException exception = assertThrows(DomainException.class, () -> service.disburse(command()));

        assertEquals(PolicyErrorCode.POLICY_NOT_EXIST, exception.getErrorCode());
        verifyNoInteractions(commandGateway);
    }

    @Test
    void shouldLoadThroughRealEventSourcingRepositoryOutsideCommandContext() {
        // 真机回归（g04-02 验收暴露）：loadPolicy 先于命令派发执行，线程无 UnitOfWork，
        // 修复前此处抛「No UnitOfWork is currently started for this thread」（HTTP 500）。
        // 用真实 EventSourcingRepository（内存事件存储）复现：事件流为空时应得到业务码
        // POLICY_NOT_EXIST，而非框架级 IllegalStateException。
        EventSourcingRepository<Policy> realRepository = EventSourcingRepository.builder(Policy.class)
                .eventStore(EmbeddedEventStore.builder()
                        .storageEngine(new InMemoryEventStorageEngine())
                        .build())
                .build();
        PolicyLoanApplicationService realService = new PolicyLoanApplicationService(commandGateway, realRepository);

        DomainException exception = assertThrows(DomainException.class, () -> realService.disburse(command()));

        assertEquals(PolicyErrorCode.POLICY_NOT_EXIST, exception.getErrorCode());
        verifyNoInteractions(commandGateway);
    }

    // ==================== 夹具 ====================

    private DisbursePolicyLoanCommand command() {
        return DisbursePolicyLoanCommand.of(TENANT_ID, POLICY_ID, "L1",
                new BigDecimal("1000.00"), new BigDecimal("0.050000"), LocalDateTime.now(), "op-1");
    }

    private Policy policyWith(PolicyStatusCode statusCode) {
        return Policy.builder()
                .policyId(POLICY_ID)
                .tenantId(TENANT_ID)
                .status(new PolicyStatus(statusCode, LocalDateTime.now(), "测试状态", "op-1"))
                .build();
    }

    /** load 返回聚合包装，经 invoke 提取聚合根（与生产代码一致的 mock 形态）。 */
    @SuppressWarnings("unchecked")
    private void stubAggregateLoad(Policy policy) {
        Aggregate<Policy> aggregate = mock(Aggregate.class);
        when(aggregate.invoke(any())).thenAnswer(invocation -> {
            Function<Policy, Object> function = invocation.getArgument(0);
            return function.apply(policy);
        });
        when(policyRepository.load(POLICY_ID)).thenReturn(aggregate);
    }
}
