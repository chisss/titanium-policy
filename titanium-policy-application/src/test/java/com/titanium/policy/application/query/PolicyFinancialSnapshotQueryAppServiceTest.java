package com.titanium.policy.application.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.modelling.command.Aggregate;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.axonframework.modelling.command.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.billing.BillingEnum.ReconciliationStatus;
import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyLineStatus;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.exception.DomainException;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.aggregate.Policy;
import com.titanium.policy.entity.PaymentRecord;
import com.titanium.policy.entity.policy.PolicyProduct;
import com.titanium.policy.port.billing.BillingServicePort;
import com.titanium.policy.port.product.PolicyCashValuePort;
import com.titanium.policy.repository.PolicyLoanAccountRepository;
import com.titanium.policy.valueobject.billing.UnpaidBill;
import com.titanium.policy.valueobject.financial.PolicyFinancialSnapshot;
import com.titanium.policy.valueobject.loan.PolicyLoanAccount;
import com.titanium.policy.valueobject.policy.PolicyPeriod;
import com.titanium.policy.valueobject.product.PolicyCashValue;

/**
 * 保单财务快照取数编排测试（g04-02）。
 * <p>
 * 核心判据：<b>写侧强一致</b>（经 {@link Repository} 重建聚合，绝不回退读模型）与
 * <b>失败关闭</b>（缺保单/缺要素/外部取数失败一律抛领域异常，禁止 orElse(ZERO) 兜底）；
 * 「无记录 = 0」的业务事实与「取不到」严格区分。
 * </p>
 */
class PolicyFinancialSnapshotQueryAppServiceTest {

    private static final String TENANT_ID = "T1";
    private static final String POLICY_ID = "P1";
    /** 主险段基本保额（g04-05 减额缴清核定「新保额 ≤ 原保额」的原保额基准）。 */
    private static final String ORIGINAL_SUM_INSURED = "50000";

    private final Repository<Policy> policyRepository = mock(Repository.class);
    private final PolicyCashValuePort policyCashValuePort = mock(PolicyCashValuePort.class);
    private final BillingServicePort billingServicePort = mock(BillingServicePort.class);
    private final PolicyLoanAccountRepository policyLoanAccountRepository = mock(PolicyLoanAccountRepository.class);

    private PolicyFinancialSnapshotQueryAppService service;

    @BeforeEach
    void setUp() {
        service = new PolicyFinancialSnapshotQueryAppService(
                policyRepository, policyCashValuePort, billingServicePort, policyLoanAccountRepository);
    }

    @Test
    void shouldAssembleSnapshotWithStrongConsistency() {
        stubAggregateLoad(policyWithBasis());
        when(policyCashValuePort.getCashValue(eq(TENANT_ID), eq("PROD1"), eq(2), any(), any(), any()))
                .thenReturn(Optional.of(cashValue(new BigDecimal("8800.00"))));
        when(billingServicePort.getUnpaidBills(POLICY_ID))
                .thenReturn(List.of(new UnpaidBill("B1", new BigDecimal("500.00"))));
        when(policyLoanAccountRepository.findOutstanding(POLICY_ID, TENANT_ID))
                .thenReturn(List.of(new PolicyLoanAccount(new BigDecimal("10000.00"), new BigDecimal("600.00"))));

        PolicyFinancialSnapshot snapshot = service.query(TENANT_ID, POLICY_ID);

        assertMoney(snapshot.cashValue(), "8800.00");
        assertMoney(snapshot.sumInsured(), ORIGINAL_SUM_INSURED);
        // 主险段标识与保额同批下发：它就是减额缴清回写 policy.coverage.sumInsured 的 objectId
        assertEquals("PP-MAIN", snapshot.mainSegmentId());
        assertMoney(snapshot.loanOutstandingPrincipal(), "10000");
        // 单利 365 天整年：10000 × 0.06 × 365 / 365 = 600
        assertMoney(snapshot.loanOutstandingInterest(), "600.00");
        assertMoney(snapshot.arrearsAmount(), "500.00");
        assertMoney(snapshot.dividendBalance(), "300.00");
        assertMoney(snapshot.accountValue(), "20000");
        assertEquals(LocalDate.of(2025, 1, 1), snapshot.effectiveDate());
        assertEquals(2, snapshot.paidInstallments());
    }

    @Test
    void shouldFailClosedWhenPolicyMissing() {
        when(policyRepository.load(POLICY_ID))
                .thenThrow(new AggregateNotFoundException(POLICY_ID, "聚合不存在"));

        DomainException exception = assertThrows(DomainException.class, () -> service.query(TENANT_ID, POLICY_ID));
        assertEquals(PolicyErrorCode.POLICY_NOT_EXIST, exception.getErrorCode());
    }

    @Test
    void shouldFailClosedWhenTenantMismatch() {
        Policy policy = policyWithBasis();
        when(policy.getTenantId()).thenReturn("OTHER");
        stubAggregateLoad(policy);

        DomainException exception = assertThrows(DomainException.class, () -> service.query(TENANT_ID, POLICY_ID));
        assertEquals(PolicyErrorCode.POLICY_NOT_EXIST, exception.getErrorCode());
    }

    @Test
    void shouldFailClosedWhenEffectiveDateMissing() {
        Policy policy = policyWithBasis();
        when(policy.getPolicyPeriod()).thenReturn(null);
        stubAggregateLoad(policy);

        DomainException exception = assertThrows(DomainException.class, () -> service.query(TENANT_ID, POLICY_ID));
        assertEquals(PolicyErrorCode.POLICY_FINANCIAL_DATA_MISSING, exception.getErrorCode());
    }

    @Test
    void shouldFailClosedWhenPremiumBasisMissing() {
        Policy policy = policyWithBasis();
        when(policy.getProductId()).thenReturn(null);
        stubAggregateLoad(policy);

        DomainException exception = assertThrows(DomainException.class, () -> service.query(TENANT_ID, POLICY_ID));
        assertEquals(PolicyErrorCode.POLICY_FINANCIAL_DATA_MISSING, exception.getErrorCode());
    }

    @Test
    void shouldFailClosedWhenPolicyProductsEmpty() {
        // 现金价值取数已放行（与下方「原保额缺失」结论不同，锁定失败归因于原保额分支）
        stubCashValueAvailable();
        Policy policy = policyWithBasis();
        when(policy.getPolicyProducts()).thenReturn(List.of());
        stubAggregateLoad(policy);

        DomainException exception = assertThrows(DomainException.class, () -> service.query(TENANT_ID, POLICY_ID));
        assertEquals(PolicyErrorCode.POLICY_FINANCIAL_DATA_MISSING, exception.getErrorCode());
    }

    @Test
    void shouldFailClosedWhenMainLineAbsent() {
        // 只有附加险段：不得以附加险保额冒充原保额（取数口径只认主险段）
        stubCashValueAvailable();
        Policy policy = policyWithBasis();
        when(policy.getPolicyProducts()).thenReturn(List.of(riderProduct()));
        stubAggregateLoad(policy);

        DomainException exception = assertThrows(DomainException.class, () -> service.query(TENANT_ID, POLICY_ID));
        assertEquals(PolicyErrorCode.POLICY_FINANCIAL_DATA_MISSING, exception.getErrorCode());
    }

    @Test
    void shouldFailClosedWhenMainLineSumInsuredBlank() {
        // 主险段在、保额空白：不得以 0 兜底
        stubCashValueAvailable();
        Policy policy = policyWithBasis();
        when(policy.getPolicyProducts()).thenReturn(List.of(mainProduct(null)));
        stubAggregateLoad(policy);

        DomainException exception = assertThrows(DomainException.class, () -> service.query(TENANT_ID, POLICY_ID));
        assertEquals(PolicyErrorCode.POLICY_FINANCIAL_DATA_MISSING, exception.getErrorCode());
    }

    @Test
    void shouldFailClosedWhenMainLineIdBlank() {
        // 主险段在、保额在，但段标识空白：PolicyProduct 对标识无构造器校验，故来源侧须自守——
        // 空白 objectId 下发到跨域契约会让下游字段执行器的逐字比对失败，属「坏数据出域」
        stubCashValueAvailable();
        Policy policy = policyWithBasis();
        when(policy.getPolicyProducts()).thenReturn(
                List.of(product("  ", ProductCategory.MAIN, ORIGINAL_SUM_INSURED)));
        stubAggregateLoad(policy);

        DomainException exception = assertThrows(DomainException.class, () -> service.query(TENANT_ID, POLICY_ID));
        assertEquals(PolicyErrorCode.POLICY_FINANCIAL_DATA_MISSING, exception.getErrorCode());
    }

    @Test
    void shouldFailClosedWhenCashValueUnavailable() {
        stubAggregateLoad(policyWithBasis());
        when(policyCashValuePort.getCashValue(eq(TENANT_ID), eq("PROD1"), eq(2), any(), any(), any()))
                .thenReturn(Optional.empty());

        DomainException exception = assertThrows(DomainException.class, () -> service.query(TENANT_ID, POLICY_ID));
        assertEquals(PolicyErrorCode.POLICY_FINANCIAL_DATA_MISSING, exception.getErrorCode());
    }

    @Test
    void shouldTreatNoRecordsAsZeroBusinessFact() {
        Policy policy = policyWithBasis();
        when(policy.getAccumulatedDividend()).thenReturn(null);
        when(policy.getInvestmentAccountValue()).thenReturn(null);
        when(policy.getPaymentRecords()).thenReturn(List.of());
        stubAggregateLoad(policy);
        when(policyCashValuePort.getCashValue(eq(TENANT_ID), eq("PROD1"), eq(2), any(), any(), any()))
                .thenReturn(Optional.of(cashValue(new BigDecimal("8800.00"))));
        when(billingServicePort.getUnpaidBills(POLICY_ID)).thenReturn(List.of());
        when(policyLoanAccountRepository.findOutstanding(POLICY_ID, TENANT_ID)).thenReturn(List.of());

        PolicyFinancialSnapshot snapshot = service.query(TENANT_ID, POLICY_ID);

        // 无未结贷款/无欠费/无红利/无账户 = 0，是业务事实（非 orElse(ZERO) 兜底）
        assertMoney(snapshot.loanOutstandingPrincipal(), "0");
        assertMoney(snapshot.loanOutstandingInterest(), "0");
        assertMoney(snapshot.arrearsAmount(), "0");
        assertMoney(snapshot.dividendBalance(), "0");
        assertMoney(snapshot.accountValue(), "0");
        assertEquals(0, snapshot.paidInstallments());
    }

    @Test
    void shouldSumOutstandingAmountsAcrossLoanAccounts() {
        stubAggregateLoad(policyWithBasis());
        when(policyCashValuePort.getCashValue(eq(TENANT_ID), eq("PROD1"), eq(2), any(), any(), any()))
                .thenReturn(Optional.of(cashValue(new BigDecimal("1.00"))));
        // 两笔未结贷款：本层只做汇总，计息口径由贷款账户聚合独担（不在此重算）
        when(policyLoanAccountRepository.findOutstanding(POLICY_ID, TENANT_ID))
                .thenReturn(List.of(
                        new PolicyLoanAccount(new BigDecimal("10000.00"), new BigDecimal("600.00")),
                        new PolicyLoanAccount(new BigDecimal("5000.00"), new BigDecimal("150.00"))));

        PolicyFinancialSnapshot snapshot = service.query(TENANT_ID, POLICY_ID);

        assertMoney(snapshot.loanOutstandingPrincipal(), "15000.00");
        assertMoney(snapshot.loanOutstandingInterest(), "750.00");
    }

    @Test
    void shouldCountOnlyReconciledPaymentsAsPaidInstallments() {
        Policy policy = policyWithBasis();
        when(policy.getPaymentRecords()).thenReturn(List.of(
                payment(ReconciliationStatus.RECONCILED),
                payment(ReconciliationStatus.RECONCILED),
                payment(ReconciliationStatus.UNRECONCILED),
                payment(ReconciliationStatus.FAILED)));
        stubAggregateLoad(policy);
        when(policyCashValuePort.getCashValue(eq(TENANT_ID), eq("PROD1"), eq(2), any(), any(), any()))
                .thenReturn(Optional.of(cashValue(new BigDecimal("1.00"))));
        when(policyLoanAccountRepository.findOutstanding(POLICY_ID, TENANT_ID)).thenReturn(List.of());

        PolicyFinancialSnapshot snapshot = service.query(TENANT_ID, POLICY_ID);

        assertEquals(2, snapshot.paidInstallments());
    }

    @Test
    void shouldPropagateBillingFailureWithoutSwallowing() {
        stubAggregateLoad(policyWithBasis());
        when(policyCashValuePort.getCashValue(eq(TENANT_ID), eq("PROD1"), eq(2), any(), any(), any()))
                .thenReturn(Optional.of(cashValue(new BigDecimal("1.00"))));
        when(billingServicePort.getUnpaidBills(POLICY_ID))
                .thenThrow(new DomainException(PolicyErrorCode.POLICY_FINANCIAL_DATA_MISSING, "计费域应答异常"));

        DomainException exception = assertThrows(DomainException.class, () -> service.query(TENANT_ID, POLICY_ID));
        assertEquals(PolicyErrorCode.POLICY_FINANCIAL_DATA_MISSING, exception.getErrorCode());
        verify(policyRepository).load(POLICY_ID);
    }

    @Test
    void shouldLoadThroughRealEventSourcingRepositoryOutsideCommandContext() {
        // 真机回归（g04-02 验收暴露）：查询路径无命令上下文，ES 仓储 load 要求线程内有 UnitOfWork，
        // 修复前此处抛「No UnitOfWork is currently started for this thread」（HTTP 500）。
        // 用真实 EventSourcingRepository（内存事件存储）复现：事件流为空时应得到业务码
        // POLICY_NOT_EXIST，而非框架级 IllegalStateException。
        EventSourcingRepository<Policy> realRepository = EventSourcingRepository.builder(Policy.class)
                .eventStore(EmbeddedEventStore.builder()
                        .storageEngine(new InMemoryEventStorageEngine())
                        .build())
                .build();
        PolicyFinancialSnapshotQueryAppService realService = new PolicyFinancialSnapshotQueryAppService(
                realRepository, policyCashValuePort, billingServicePort, policyLoanAccountRepository);

        DomainException exception = assertThrows(DomainException.class,
                () -> realService.query(TENANT_ID, POLICY_ID));

        assertEquals(PolicyErrorCode.POLICY_NOT_EXIST, exception.getErrorCode());
    }

    // ==================== 夹具 ====================

    /**
     * 现金价值要素齐备的保单替身（生效日 2025-01-01，估值日跨两年 → 保单年度 2）。
     * <p>
     * 🔴 用 mock 替身而非构造器/事件回放：本类测试的本质是「给取数编排喂数据」，且 fail-closed
     * 用例需要表达「畸形状态」（policyPeriod=null 等）——事件回放构造的合法状态表达不了；聚合
     * builder 与无参构造器已按 DSN-02 收紧（去 SuperBuilder、构造器 protected），外部亦不可
     * 凭空构造。缺省未 stub 的 getter 返回 null，天然对应「要素缺失」，由各失败用例按需覆写。
     * </p>
     */
    private Policy policyWithBasis() {
        Policy policy = mock(Policy.class);
        when(policy.getPolicyId()).thenReturn(POLICY_ID);
        when(policy.getTenantId()).thenReturn(TENANT_ID);
        when(policy.getProductId()).thenReturn("PROD1");
        when(policy.getTotalPremium()).thenReturn(Money.of(new BigDecimal("10000"), "CNY"));
        when(policy.getPolicyPeriod()).thenReturn(PolicyPeriod.of(LocalDateTime.of(2025, 1, 1, 0, 0), null));
        when(policy.getPolicyProducts()).thenReturn(List.of(mainProduct(ORIGINAL_SUM_INSURED)));
        when(policy.mainLine()).thenReturn(mainProduct(ORIGINAL_SUM_INSURED));
        when(policy.getAccumulatedDividend()).thenReturn(Money.of(new BigDecimal("300.00"), "CNY"));
        when(policy.getInvestmentAccountValue()).thenReturn(new BigDecimal("20000"));
        when(policy.getPaymentRecords()).thenReturn(List.of(
                payment(ReconciliationStatus.RECONCILED),
                payment(ReconciliationStatus.RECONCILED)));
        return policy;
    }

    /** 主险段（减额缴清「原保额」的取数口径：只认主险段，附加险段不参与该不等式）。 */
    private PolicyProduct mainProduct(String sumInsured) {
        return product("PP-MAIN", ProductCategory.MAIN, sumInsured);
    }

    /** 附加险段（挂于主险段之下；不得被用作原保额）。 */
    private PolicyProduct riderProduct() {
        return product("PP-RIDER", ProductCategory.RIDER, "999999");
    }

    private PolicyProduct product(String id, ProductCategory category, String sumInsured) {
        return new PolicyProduct(id, 1, category, null, "PROD1", "CODE1", "险种", "V1.0",
                InsuranceProductType.MEDICAL,
                sumInsured != null ? Money.of(new BigDecimal(sumInsured), "CNY") : null,
                Money.of(new BigDecimal("10000"), "CNY"), null, null, null, PolicyLineStatus.ACCEPTED,
                List.of(), List.of(), List.of());
    }

    /** 现金价值取数放行（使失败归因可精确落到「原保额」分支，而非被前序步骤掩盖）。 */
    private void stubCashValueAvailable() {
        when(policyCashValuePort.getCashValue(eq(TENANT_ID), eq("PROD1"), eq(2), any(), any(), any()))
                .thenReturn(Optional.of(cashValue(new BigDecimal("1.00"))));
    }

    private PaymentRecord payment(ReconciliationStatus status) {
        return new PaymentRecord("PAY_" + status, "PN_" + status, Money.of(new BigDecimal("1000"), "CNY"),
                LocalDateTime.now(), null, status);
    }

    private PolicyCashValue cashValue(BigDecimal value) {
        return new PolicyCashValue("PROD1", 2, false, "NORMAL", new BigDecimal("0.88"),
                value, "SV-1", "1", "HASH");
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

    /** BigDecimal 比较（equals 对 scale 敏感，用 compareTo）。 */
    private void assertMoney(BigDecimal actual, String expected) {
        assertTrue(actual != null && actual.compareTo(new BigDecimal(expected)) == 0,
                "期望 " + expected + " 实际 " + actual);
    }
}
