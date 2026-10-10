package com.titanium.policy.aggregate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.policy.command.RepayPolicyLoanCommand;
import com.titanium.policy.entity.loan.PolicyLoanEntry;
import com.titanium.policy.entity.loan.PolicyLoanRepaymentRecord;
import com.titanium.policy.event.PolicyLoanDisbursedEvent;
import com.titanium.policy.event.PolicyLoanRepaidEvent;
import com.titanium.policy.exception.PolicyBusinessRuleException;

/**
 * 保单贷款账户聚合根·还款行为测试（g04-04）。
 * <p>
 * 覆盖「先息后本 + 单利 365」的计息与分配、部分还款与提前清偿、结清标记、超额拒绝、幂等重试、
 * 明细不存在/非正金额/倒序日期/跨租户等失败关闭面。
 * </p>
 * <p>
 * 🔴 <b>AC-05 口径一致性</b>：{@link #shouldAccrueInterestInLineWithMaintenanceFormula()} 的黄金算例表
 * 与保全域 {@code PolicyLoanDetail.accruedInterest(days)} <b>同表</b>（{@code maintenance} 侧
 * {@code PolicyLoanRepaymentTest} 复用同一组数），两侧各自断言同一期望值即等价于两域口径逐位一致——
 * <b>改动任一侧算例必须同步另一侧</b>。
 * </p>
 */
class PolicyLoanRepaymentTest {

    private FixtureConfiguration<PolicyLoan> fixture;

    private static final String    POLICY_ID = "policy-001";
    private static final String    TENANT_ID = "tenant-001";
    private static final String    LOAN_ID   = "LOAN-0001";
    /** 放款日：取整日，便于与还款日之间构造整自然日，与保全域计息口径逐位比对 */
    private static final LocalDateTime LOAN_DATE = LocalDateTime.of(2026, 1, 1, 10, 0);

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(PolicyLoan.class);
        fixture.setReportIllegalStateChange(false);
    }

    /**
     * AC-05 正向判据：首个计息区间（放款日 → 还款日）的利息，
     * 与保全域 {@code PolicyLoanDetail.accruedInterest(days)} 同表黄金值逐位一致。
     */
    @Test
    void shouldAccrueInterestInLineWithMaintenanceFormula() {
        // (计息天数, 期望利息) —— 本金 900.00、年利率 0.05、单利 365、scale 2 HALF_UP
        // 🔴 与 maintenance 域 PolicyLoanRepaymentTest 共用本表，改动须两侧同步
        Object[][] cases = {{90, "11.10"}, {365, "45.00"}, {30, "3.70"}, {1, "0.12"}};
        for (Object[] each : cases) {
            int days = (int) each[0];
            PolicyLoanEntry entry = new PolicyLoanEntry(LOAN_ID, new BigDecimal("900.00"),
                    new BigDecimal("0.05"), LOAN_DATE, List.of());
            assertEquals(new BigDecimal((String) each[1]),
                    entry.accruedInterestTo(LOAN_DATE.plusDays(days)),
                    "第 " + days + " 天利息应与保全域 PolicyLoanDetail.accruedInterest 同口径");
        }
    }

    /** 全额还款（本金 + 全部利息）→ 结清：先息后本分配与余额快照。 */
    @Test
    void shouldSettleOnFullRepayment() {
        fixture.given(disbursedEvent("900.00", "0.05"))
                .when(repayCommand("REPAY-1", "911.10", LOAN_DATE.plusDays(90)))
                .expectEvents(new PolicyLoanRepaidEvent(PolicyLoan.loanAccountIdOf(POLICY_ID),
                        POLICY_ID, LOAN_ID, "REPAY-1", new BigDecimal("911.10"),
                        new BigDecimal("11.10"), new BigDecimal("900.00"), new BigDecimal("11.10"),
                        new BigDecimal("0.00"), new BigDecimal("0.00"), true,
                        LOAN_DATE.plusDays(90), "op-1", TENANT_ID))
                .expectState(account -> assertTrue(account.outstandingEntries().isEmpty()));
    }

    /** 部分还款：先冲利息（冲尽），余额冲本金；未结清。 */
    @Test
    void shouldAllocateInterestFirstOnPartialRepayment() {
        fixture.given(disbursedEvent("900.00", "0.05"))
                .when(repayCommand("REPAY-1", "100.00", LOAN_DATE.plusDays(90)))
                .expectEvents(new PolicyLoanRepaidEvent(PolicyLoan.loanAccountIdOf(POLICY_ID),
                        POLICY_ID, LOAN_ID, "REPAY-1", new BigDecimal("100.00"),
                        new BigDecimal("11.10"), new BigDecimal("88.90"), new BigDecimal("11.10"),
                        new BigDecimal("811.10"), new BigDecimal("0.00"), false,
                        LOAN_DATE.plusDays(90), "op-1", TENANT_ID))
                .expectState(account -> assertFalse(account.outstandingEntries().isEmpty()));
    }

    /** 二次还款：计息基数已降为未偿本金（分段计息，不重复计已偿部分）。 */
    @Test
    void shouldAccrueOnReducedPrincipalForSecondRepayment() {
        fixture.given(disbursedEvent("900.00", "0.05"),
                        repaidEvent("REPAY-1", "100.00", "11.10", "88.90", "11.10", 90))
                // 第二段：未偿本金 811.10，2026-04-01 → 2026-05-01 共 30 天
                // 811.10 × 0.05 × 30 / 365 = 3.3332… → 3.33
                .when(repayCommand("REPAY-2", "814.43", LOAN_DATE.plusDays(120)))
                .expectEvents(new PolicyLoanRepaidEvent(PolicyLoan.loanAccountIdOf(POLICY_ID),
                        POLICY_ID, LOAN_ID, "REPAY-2", new BigDecimal("814.43"),
                        new BigDecimal("3.33"), new BigDecimal("811.10"), new BigDecimal("3.33"),
                        new BigDecimal("0.00"), new BigDecimal("0.00"), true,
                        LOAN_DATE.plusDays(120), "op-1", TENANT_ID))
                .expectState(account -> assertTrue(account.outstandingEntries().isEmpty()));
    }

    /** AC-05 反向判据：还款额超过欠款总额（未偿本金 + 已计未付利息）即拒绝，不静默截断。 */
    @Test
    void shouldRejectRepaymentExceedingOutstanding() {
        fixture.given(disbursedEvent("900.00", "0.05"))
                .when(repayCommand("REPAY-1", "911.11", LOAN_DATE.plusDays(90)))
                .expectException(PolicyBusinessRuleException.class)
                .expectExceptionMessage("还款额超过欠款总额: 还款额 911.11 > 欠款总额 911.10");
    }

    /** 幂等重试：同还款业务号同额 → 不产生新事件，回显既有明细（分配以首次入账为准）。 */
    @Test
    void shouldReturnExistingEntryOnIdempotentRetry() {
        fixture.given(disbursedEvent("900.00", "0.05"),
                        repaidEvent("REPAY-1", "100.00", "11.10", "88.90", "11.10", 90))
                .when(repayCommand("REPAY-1", "100.00", LOAN_DATE.plusDays(90)))
                .expectEvents()
                .expectResultMessagePayload(entryAfterFirstRepayment());
    }

    /** 同还款业务号异额 → 调用方串号，显式拒绝。 */
    @Test
    void shouldRejectDuplicateRepaymentIdWithDifferentAmount() {
        fixture.given(disbursedEvent("900.00", "0.05"),
                        repaidEvent("REPAY-1", "100.00", "11.10", "88.90", "11.10", 90))
                .when(repayCommand("REPAY-1", "200.00", LOAN_DATE.plusDays(90)))
                .expectException(PolicyBusinessRuleException.class)
                .expectExceptionMessage("还款业务号重复且金额不一致: REPAY-1");
    }

    /** 还款额非正 → 拒绝。 */
    @Test
    void shouldRejectNonPositiveRepaymentAmount() {
        fixture.given(disbursedEvent("900.00", "0.05"))
                .when(repayCommand("REPAY-1", "0", LOAN_DATE.plusDays(90)))
                .expectException(PolicyBusinessRuleException.class)
                .expectExceptionMessage("还款金额必须为正数: 0");
    }

    /** 目标明细不存在 → 拒绝（不做跨明细抵扣）。 */
    @Test
    void shouldRejectUnknownLoanId() {
        fixture.given(disbursedEvent("900.00", "0.05"))
                .when(RepayPolicyLoanCommand.of(TENANT_ID, POLICY_ID, "LOAN-UNKNOWN", "REPAY-1",
                        new BigDecimal("100.00"), LOAN_DATE.plusDays(90), "op-1"))
                .expectException(PolicyBusinessRuleException.class)
                .expectExceptionMessage("贷款明细不存在: LOAN-UNKNOWN");
    }

    /** 还款日期早于上次计息日 → 拒绝（禁止倒序计息区间）。 */
    @Test
    void shouldRejectRepaymentDateBeforeLastAccrualDate() {
        fixture.given(disbursedEvent("900.00", "0.05"),
                        repaidEvent("REPAY-1", "100.00", "11.10", "88.90", "11.10", 90))
                .when(repayCommand("REPAY-2", "100.00", LOAN_DATE.plusDays(30)))
                .expectException(PolicyBusinessRuleException.class)
                .expectExceptionMessage("还款日期不得早于上次计息日: " + LOAN_DATE.plusDays(90));
    }

    /** 跨租户还款 → 拒绝（不得泄漏资源存在性）。 */
    @Test
    void shouldRejectCrossTenantRepayment() {
        fixture.given(disbursedEvent("900.00", "0.05"))
                .when(RepayPolicyLoanCommand.of("tenant-other", POLICY_ID, LOAN_ID, "REPAY-1",
                        new BigDecimal("100.00"), LOAN_DATE.plusDays(90), "op-1"))
                .expectException(PolicyBusinessRuleException.class)
                .expectExceptionMessage("保单贷款账户不存在");
    }

    /** 冻结利率缺失（数据异常）→ 计息失败关闭，不得以 0 兜底。 */
    @Test
    void shouldFailClosedWhenInterestRateMissingOnAccrual() {
        PolicyLoanEntry entry = new PolicyLoanEntry(LOAN_ID, new BigDecimal("900.00"), null,
                LOAN_DATE, List.of());

        PolicyBusinessRuleException exception = assertThrows(PolicyBusinessRuleException.class,
                () -> entry.accruedInterestTo(LOAN_DATE.plusDays(90)));

        assertEquals(PolicyErrorCode.POLICY_FINANCIAL_DATA_MISSING, exception.getErrorCode());
    }

    /** 未偿利息 = 已计未付 + 当前区间增量（对外取数契约口径，与还款分配同源）。 */
    @Test
    void shouldComposeOutstandingInterestFromUnpaidPlusCurrentSegment() {
        PolicyLoanEntry entry = entryAfterFirstRepayment();

        // 首次还款后本金 811.10、已计未付 0；LOAN_DATE+120 即再计 30 天：811.10 × 0.05 × 30 / 365 = 3.33
        assertEquals(0, new BigDecimal("3.33")
                .compareTo(entry.outstandingInterestAt(LOAN_DATE.plusDays(120))));
    }

    /** 首次部分还款后的明细（幂等回显的比对基准：回显须与首次入账逐字段一致）。 */
    private PolicyLoanEntry entryAfterFirstRepayment() {
        return new PolicyLoanEntry(LOAN_ID, new BigDecimal("900.00"), new BigDecimal("0.05"), LOAN_DATE,
                List.of(new PolicyLoanRepaymentRecord("REPAY-1", new BigDecimal("100.00"),
                        new BigDecimal("11.10"), new BigDecimal("88.90"), new BigDecimal("11.10"),
                        LOAN_DATE.plusDays(90))));
    }

    private RepayPolicyLoanCommand repayCommand(String repaymentId, String amount, LocalDateTime repaymentDate) {
        return RepayPolicyLoanCommand.of(TENANT_ID, POLICY_ID, LOAN_ID, repaymentId,
                new BigDecimal(amount), repaymentDate, "op-1");
    }

    private PolicyLoanDisbursedEvent disbursedEvent(String amount, String rate) {
        return new PolicyLoanDisbursedEvent(PolicyLoan.loanAccountIdOf(POLICY_ID), LOAN_ID, POLICY_ID,
                new BigDecimal(amount), new BigDecimal(rate), LOAN_DATE, "op-1", TENANT_ID);
    }

    private PolicyLoanRepaidEvent repaidEvent(String repaymentId, String amount, String interestPortion,
            String principalPortion, String accruedInterest, int days) {
        return new PolicyLoanRepaidEvent(PolicyLoan.loanAccountIdOf(POLICY_ID), POLICY_ID, LOAN_ID,
                repaymentId, new BigDecimal(amount), new BigDecimal(interestPortion),
                new BigDecimal(principalPortion), new BigDecimal(accruedInterest),
                new BigDecimal("811.10"), new BigDecimal("0.00"), false,
                LOAN_DATE.plusDays(days), "op-1", TENANT_ID);
    }
}
