package com.titanium.policy.web.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.common.context.RequestContextHolder;
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.exception.DomainException;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.response.policy.PolicyFinancialResponse;
import com.titanium.policy.application.query.PolicyFinancialSnapshotQueryAppService;
import com.titanium.policy.valueobject.financial.PolicyFinancialSnapshot;

/**
 * 保单财务快照 Provider 测试（g04-02）。
 * <p>
 * 本类只守 Provider 职责边界：租户取自请求上下文、保单号取自路径变量、快照 14 字段
 * 原样映射进响应，取数编排失败时领域异常<b>原样透传</b>（由全局异常处理器兜底，
 * Provider 不兜底、不伪造金额）。
 * </p>
 */
class PolicyFinancialApiProviderTest {

    @BeforeEach
    void setUp() {
        RequestContextHolder.setTenantId("tenant-1");
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void shouldMapSnapshotFieldsIntoResponse() {
        PolicyFinancialSnapshotQueryAppService queryService = mock(PolicyFinancialSnapshotQueryAppService.class);
        PolicyFinancialApiProvider provider = new PolicyFinancialApiProvider(queryService);
        LocalDate effectiveDate = LocalDate.of(2025, 1, 1);
        when(queryService.query("tenant-1", "policy-1")).thenReturn(new PolicyFinancialSnapshot(
                new BigDecimal("8800.00"),
                new BigDecimal("50000"),
                "PP-MAIN",
                new BigDecimal("10000"),
                new BigDecimal("600.00"),
                new BigDecimal("500.00"),
                new BigDecimal("300.00"),
                new BigDecimal("20000"),
                effectiveDate,
                2,
                "ISSUE-BIZ-1",
                new BigDecimal("3650.00"),
                LocalDate.of(2025, 12, 31),
                false));

        ApiResponse<PolicyFinancialResponse> response = provider.getFinancialSnapshot("policy-1");

        verify(queryService).query("tenant-1", "policy-1");
        assertTrue(response.isSuccess());
        PolicyFinancialResponse data = response.getData();
        // 保单号来自路径变量，与快照字段无关（快照本身不含 policyId）
        assertEquals("policy-1", data.policyId());
        assertEquals(new BigDecimal("8800.00"), data.cashValue());
        assertEquals(new BigDecimal("50000"), data.sumInsured());
        assertEquals("PP-MAIN", data.mainSegmentId());
        assertEquals(new BigDecimal("10000"), data.loanOutstandingPrincipal());
        assertEquals(new BigDecimal("600.00"), data.loanOutstandingInterest());
        assertEquals(new BigDecimal("500.00"), data.arrearsAmount());
        assertEquals(new BigDecimal("300.00"), data.dividendBalance());
        assertEquals(new BigDecimal("20000"), data.accountValue());
        assertEquals(effectiveDate, data.effectiveDate());
        assertEquals(2, data.paidInstallments());
        // 退费口径四项：跨域契约里少映射任一项，下游未满期保费只能按「事实缺失」拒绝，且编译期无感
        assertEquals("ISSUE-BIZ-1", data.issuanceBizNo());
        assertEquals(new BigDecimal("3650.00"), data.annualPremium());
        assertEquals(LocalDate.of(2025, 12, 31), data.coverageEndDate());
        assertEquals(false, data.premiumPaidUp());
    }

    @Test
    void shouldPropagateDomainExceptionWithoutSwallowing() {
        PolicyFinancialSnapshotQueryAppService queryService = mock(PolicyFinancialSnapshotQueryAppService.class);
        PolicyFinancialApiProvider provider = new PolicyFinancialApiProvider(queryService);
        DomainException expected = new DomainException(PolicyErrorCode.POLICY_NOT_EXIST, "保单不存在: policy-1");
        when(queryService.query("tenant-1", "policy-1")).thenThrow(expected);

        // Provider 不兜底：缺数据/保单不存在的领域异常原样上抛，由全局异常处理器转业务码失败响应
        DomainException exception = assertThrows(DomainException.class,
                () -> provider.getFinancialSnapshot("policy-1"));

        assertSame(expected, exception);
        assertEquals(PolicyErrorCode.POLICY_NOT_EXIST, exception.getErrorCode());
    }
}
