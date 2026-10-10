package com.titanium.policy.aggregate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.policy.command.DisbursePolicyLoanCommand;
import com.titanium.policy.entity.loan.PolicyLoanEntry;
import com.titanium.policy.event.PolicyLoanDisbursedEvent;
import com.titanium.policy.exception.PolicyBusinessRuleException;

/**
 * 保单贷款账户聚合根·放款行为测试（g04-03）。
 * <p>
 * 覆盖首笔放款创建账户、追加放款、贷款明细业务号幂等拒绝、金额/利率值域校验、
 * 跨租户写入守护与事件回放——放款为资金事实，本金与冻结利率一经入账不可变更。
 * </p>
 */
class PolicyLoanTest {

    private FixtureConfiguration<PolicyLoan> fixture;

    private static final String POLICY_ID  = "policy-001";
    private static final String TENANT_ID  = "tenant-001";
    private static final String LOAN_ID_1  = "LOAN-0001";
    private static final String LOAN_ID_2  = "LOAN-0002";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 28, 10, 0);

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(PolicyLoan.class);
        fixture.setReportIllegalStateChange(false);
    }

    @Test
    void shouldCreateAccountOnFirstDisburse() {
        fixture.givenNoPriorActivity()
                .when(disburseCommand(LOAN_ID_1, "50000.00", "0.05"))
                .expectEvents(new PolicyLoanDisbursedEvent(PolicyLoan.loanAccountIdOf(POLICY_ID),
                        LOAN_ID_1, POLICY_ID, new BigDecimal("50000.00"), new BigDecimal("0.05"),
                        NOW, "op-1", TENANT_ID));
    }

    @Test
    void shouldAppendEntryOnSecondDisburse() {
        fixture.given(disbursedEvent(LOAN_ID_1, "50000.00", "0.05"))
                .when(disburseCommand(LOAN_ID_2, "20000.00", "0.04"))
                .expectEvents(disbursedEvent(LOAN_ID_2, "20000.00", "0.04"));
    }

    @Test
    void shouldReturnExistingEntryOnIdempotentRetry() {
        fixture.given(disbursedEvent(LOAN_ID_1, "50000.00", "0.05"))
                .when(disburseCommand(LOAN_ID_1, "50000.00", "0.06"))
                .expectEvents()
                .expectResultMessagePayload(new PolicyLoanEntry(
                        LOAN_ID_1, new BigDecimal("50000.00"), new BigDecimal("0.05"), NOW, List.of()));
    }

    @Test
    void shouldRejectDuplicateLoanIdWithDifferentAmount() {
        fixture.given(disbursedEvent(LOAN_ID_1, "50000.00", "0.05"))
                .when(disburseCommand(LOAN_ID_1, "30000.00", "0.05"))
                .expectException(PolicyBusinessRuleException.class)
                .expectExceptionMessage("贷款明细业务号重复放款且金额不一致: " + LOAN_ID_1);
    }

    @Test
    void shouldRejectNonPositiveAmount() {
        fixture.givenNoPriorActivity()
                .when(disburseCommand(LOAN_ID_1, "0", "0.05"))
                .expectException(PolicyBusinessRuleException.class)
                .expectExceptionMessage("放款金额必须为正数: 0");
    }

    @Test
    void shouldRejectRateOutOfRange() {
        fixture.givenNoPriorActivity()
                .when(disburseCommand(LOAN_ID_1, "50000.00", "1.5"))
                .expectException(PolicyBusinessRuleException.class)
                .expectExceptionMessage("冻结利率必须落在 [0,1]: 1.5");
    }

    @Test
    void shouldRejectCrossTenantDisburse() {
        fixture.given(disbursedEvent(LOAN_ID_1, "50000.00", "0.05"))
                .when(disburseCommand("tenant-other", LOAN_ID_2, "10000.00", "0.05"))
                .expectException(PolicyBusinessRuleException.class)
                .expectExceptionMessage("保单贷款账户不存在");
    }

    private DisbursePolicyLoanCommand disburseCommand(String loanId, String amount, String rate) {
        return disburseCommand(TENANT_ID, loanId, amount, rate);
    }

    private DisbursePolicyLoanCommand disburseCommand(String tenantId, String loanId, String amount, String rate) {
        return DisbursePolicyLoanCommand.of(tenantId, POLICY_ID, loanId,
                new BigDecimal(amount), new BigDecimal(rate), NOW, "op-1");
    }

    private PolicyLoanDisbursedEvent disbursedEvent(String loanId, String amount, String rate) {
        return new PolicyLoanDisbursedEvent(PolicyLoan.loanAccountIdOf(POLICY_ID), loanId, POLICY_ID,
                new BigDecimal(amount), new BigDecimal(rate), NOW, "op-1", TENANT_ID);
    }
}
