package com.titanium.policy.web.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.common.context.RequestContextHolder;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.request.policy.DisbursePolicyLoanRequest;
import com.titanium.policy.api.request.policy.RepayPolicyLoanRequest;
import com.titanium.policy.api.response.policy.DisbursePolicyLoanResponse;
import com.titanium.policy.api.response.policy.RepayPolicyLoanResponse;
import com.titanium.policy.application.command.policy.PolicyLoanApplicationService;
import com.titanium.policy.command.DisbursePolicyLoanCommand;
import com.titanium.policy.command.RepayPolicyLoanCommand;
import com.titanium.policy.entity.loan.PolicyLoanEntry;
import com.titanium.policy.entity.loan.PolicyLoanRepaymentRecord;

/**
 * 保单贷款 Provider 测试（g04-03 放款 / g04-04 还款）。
 * <p>
 * 本类只守 Provider 职责边界：租户/操作人取自请求上下文、请求字段直译进命令、
 * 回执字段与请求/聚合返回一致；失败时应用层异常<b>原样透传</b>（由全局异常处理器兜底，
 * Provider 不兜底、不伪造金额、不复算分配）。
 * </p>
 */
class PolicyLoanApiProviderTest {

    private static final LocalDateTime LOAN_DATE = LocalDateTime.of(2026, 9, 28, 10, 0);
    private static final LocalDateTime REPAY_DATE = LocalDateTime.of(2026, 12, 26, 10, 0);

    @BeforeEach
    void setUp() {
        RequestContextHolder.setTenantId("tenant-1");
        RequestContextHolder.setOperatorId("op-1");
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void shouldTranslateRequestIntoCommandAndEchoReceipt() {
        PolicyLoanApplicationService applicationService = mock(PolicyLoanApplicationService.class);
        PolicyLoanApiProvider provider = new PolicyLoanApiProvider(applicationService);
        DisbursePolicyLoanRequest request = new DisbursePolicyLoanRequest(
                "LOAN-0001", "POL-0001", new BigDecimal("50000.00"), new BigDecimal("0.05"), LOAN_DATE);
        when(applicationService.disburse(any(DisbursePolicyLoanCommand.class))).thenReturn(
                new PolicyLoanEntry("LOAN-0001", new BigDecimal("50000.00"),
                        new BigDecimal("0.05"), LOAN_DATE, List.of()));

        ApiResponse<DisbursePolicyLoanResponse> response = provider.disburse(request);

        // 请求 5 字段 + 上下文租户/操作人直译进命令，Provider 不补造任何业务字段
        verify(applicationService).disburse(argThat(command ->
                "tenant-1".equals(command.tenantId())
                        && "op-1".equals(command.operatorId())
                        && "POL-0001".equals(command.policyId())
                        && "LOAN-0001".equals(command.loanId())
                        && command.loanAmount() != null
                        && command.loanAmount().compareTo(new BigDecimal("50000.00")) == 0
                        && command.interestRate() != null
                        && command.interestRate().compareTo(new BigDecimal("0.05")) == 0
                        && LOAN_DATE.equals(command.loanDate())));
        assertTrue(response.isSuccess());
        assertEquals("LOAN-0001", response.getData().loanId());
        assertEquals("POL-0001", response.getData().policyId());
    }

    @Test
    void shouldEchoFirstDisbursementFactsOnIdempotentRetry() {
        PolicyLoanApplicationService applicationService = mock(PolicyLoanApplicationService.class);
        PolicyLoanApiProvider provider = new PolicyLoanApiProvider(applicationService);
        // 幂等重试：请求携带新利率与新日期，聚合返回首次入账明细（利率/日期以首次为准）
        DisbursePolicyLoanRequest retryRequest = new DisbursePolicyLoanRequest(
                "LOAN-0001", "POL-0001", new BigDecimal("50000.00"),
                new BigDecimal("0.06"), LOAN_DATE.plusDays(1));
        when(applicationService.disburse(any(DisbursePolicyLoanCommand.class))).thenReturn(
                new PolicyLoanEntry("LOAN-0001", new BigDecimal("50000.00"),
                        new BigDecimal("0.05"), LOAN_DATE, List.of()));

        ApiResponse<DisbursePolicyLoanResponse> response = provider.disburse(retryRequest);

        // 响应必须回显首次事实，不得以重试载荷漂移
        assertTrue(response.isSuccess());
        assertEquals(new BigDecimal("0.05"), response.getData().interestRate());
        assertEquals(LOAN_DATE, response.getData().loanDate());
    }

    @Test
    void shouldTranslateRepayRequestIntoCommandAndEchoAuthoritativeAllocation() {
        PolicyLoanApplicationService applicationService = mock(PolicyLoanApplicationService.class);
        PolicyLoanApiProvider provider = new PolicyLoanApiProvider(applicationService);
        RepayPolicyLoanRequest request = new RepayPolicyLoanRequest(
                "REPAY-0001", "POL-0001", "LOAN-0001", new BigDecimal("911.10"), REPAY_DATE);
        // 聚合返回还款后明细（分配与余额均由聚合派生，Provider 不复算）
        PolicyLoanRepaymentRecord record = new PolicyLoanRepaymentRecord("REPAY-0001",
                new BigDecimal("911.10"), new BigDecimal("11.10"), new BigDecimal("900.00"),
                new BigDecimal("11.10"), REPAY_DATE);
        when(applicationService.repay(any(RepayPolicyLoanCommand.class))).thenReturn(
                new PolicyLoanEntry("LOAN-0001", new BigDecimal("900.00"),
                        new BigDecimal("0.05"), LOAN_DATE, List.of(record)));

        ApiResponse<RepayPolicyLoanResponse> response = provider.repay(request);

        // 请求字段 + 上下文租户/操作人直译进命令
        verify(applicationService).repay(argThat(command ->
                "tenant-1".equals(command.tenantId())
                        && "op-1".equals(command.operatorId())
                        && "POL-0001".equals(command.policyId())
                        && "LOAN-0001".equals(command.loanId())
                        && "REPAY-0001".equals(command.repaymentId())
                        && command.repaymentAmount().compareTo(new BigDecimal("911.10")) == 0
                        && REPAY_DATE.equals(command.repaymentDate())));
        // 回执逐项回显权威分配（调用方据此入账/冲销，不得复算）
        assertTrue(response.isSuccess());
        assertEquals(new BigDecimal("11.10"), response.getData().interestPortion());
        assertEquals(new BigDecimal("900.00"), response.getData().principalPortion());
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getData().outstandingPrincipalAfter()));
        assertTrue(response.getData().settled());
    }
}
