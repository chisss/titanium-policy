package com.titanium.policy.application.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.policy.port.product.PolicyCashValuePort;
import com.titanium.policy.query.repository.PolicyViewRepository;
import com.titanium.policy.query.view.PolicyView;
import com.titanium.policy.valueobject.product.PolicyCashValue;

/**
 * 保单现金价值查询应用服务测试
 * <p>
 * 校验「保单号 → 保单要素 → 现金价值」的翻译：保单年度推导口径（生效日周年进位）、三项必要要素
 * 缺失时不发起远程调用、估值日缺省取当日。纯 mockito，不启动容器。
 * </p>
 */
class PolicyCashValueQueryAppServiceTest {

    private static final String TENANT_ID = "TENANT-A";
    private static final String POLICY_ID = "POL-20260911-0001";
    private static final String PRODUCT_ID = "PRD-0001";

    /** 生效于 2026-03-01 的保单读模型（保单要素齐备）。 */
    private PolicyView policyView() {
        PolicyView view = new PolicyView();
        view.setPolicyId(POLICY_ID);
        view.setTenantId(TENANT_ID);
        view.setProductId(PRODUCT_ID);
        view.setTotalPremium(new BigDecimal("12000.00"));
        view.setStartDate(LocalDateTime.of(2026, 3, 1, 0, 0));
        return view;
    }

    private PolicyViewRepository repositoryReturning(PolicyView view) {
        PolicyViewRepository repository = mock(PolicyViewRepository.class);
        when(repository.findByPolicyIdAndTenantId(anyString(), anyString()))
                .thenReturn(view != null ? Optional.of(view) : Optional.empty());
        return repository;
    }

    private PolicyCashValuePort cashValuePortReturning(PolicyCashValue value) {
        PolicyCashValuePort port = mock(PolicyCashValuePort.class);
        when(port.getCashValue(anyString(), anyString(), anyInt(), any(), any(), any()))
                .thenReturn(Optional.ofNullable(value));
        return port;
    }

    private PolicyCashValue sampleCashValue(int policyYear) {
        return new PolicyCashValue(PRODUCT_ID, policyYear, false, "CASH_VALUE", new BigDecimal("0.30"),
                new BigDecimal("3600.00"), "SVP-001", "V1", "hash-abc");
    }

    private PolicyCashValueQueryAppService service(PolicyViewRepository repository, PolicyCashValuePort port) {
        return new PolicyCashValueQueryAppService(repository, port);
    }

    @Test
    @DisplayName("【核心】以保单要素发起取数：产品ID、总保费、生效日齐备")
    void shouldQueryWithPolicyBasis() {
        PolicyViewRepository repository = repositoryReturning(policyView());
        PolicyCashValuePort port = cashValuePortReturning(sampleCashValue(1));

        service(repository, port).query(TENANT_ID, POLICY_ID, LocalDate.of(2026, 6, 1));

        verify(port).getCashValue(eq(TENANT_ID), eq(PRODUCT_ID), eq(1), eq(new BigDecimal("12000.00")),
                eq(LocalDate.of(2026, 3, 1)), eq(LocalDate.of(2026, 6, 1)));
    }

    @Test
    @DisplayName("保单年度按生效日周年进位：满一年进第 2 年度、满两年进第 3 年度")
    void shouldIncrementPolicyYearOnAnniversary() {
        PolicyViewRepository repository = repositoryReturning(policyView());
        PolicyCashValuePort port = cashValuePortReturning(sampleCashValue(3));

        service(repository, port).query(TENANT_ID, POLICY_ID, LocalDate.of(2028, 3, 1));

        ArgumentCaptor<Integer> yearCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(port).getCashValue(anyString(), anyString(), yearCaptor.capture(), any(), any(), any());
        assertEquals(3, yearCaptor.getValue(), "生效 2026-03-01，估值 2028-03-01 应为第 3 保单年度");
    }

    @Test
    @DisplayName("周年日前一日仍属当前保单年度：不提前进位")
    void shouldStayInCurrentYearBeforeAnniversary() {
        PolicyViewRepository repository = repositoryReturning(policyView());
        PolicyCashValuePort port = cashValuePortReturning(sampleCashValue(2));

        service(repository, port).query(TENANT_ID, POLICY_ID, LocalDate.of(2028, 2, 28));

        ArgumentCaptor<Integer> yearCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(port).getCashValue(anyString(), anyString(), yearCaptor.capture(), any(), any(), any());
        assertEquals(2, yearCaptor.getValue(), "周年日前一日应仍为第 2 保单年度");
    }

    @Test
    @DisplayName("生效日当天为首个保单年度（自 1 起，非第 0 年度）")
    void shouldStartFromFirstPolicyYear() {
        PolicyViewRepository repository = repositoryReturning(policyView());
        PolicyCashValuePort port = cashValuePortReturning(sampleCashValue(1));

        service(repository, port).query(TENANT_ID, POLICY_ID, LocalDate.of(2026, 3, 1));

        ArgumentCaptor<Integer> yearCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(port).getCashValue(anyString(), anyString(), yearCaptor.capture(), any(), any(), any());
        assertEquals(1, yearCaptor.getValue());
    }

    @Test
    @DisplayName("估值日缺省取当日：不因缺参拒绝查询")
    void shouldDefaultValuationDateToToday() {
        PolicyViewRepository repository = repositoryReturning(policyView());
        PolicyCashValuePort port = cashValuePortReturning(sampleCashValue(1));

        service(repository, port).query(TENANT_ID, POLICY_ID, null);

        verify(port).getCashValue(eq(TENANT_ID), eq(PRODUCT_ID), anyInt(), any(), any(), eq(LocalDate.now()));
    }

    @Test
    @DisplayName("保单不存在：返回空且不发起远程取数")
    void shouldReturnEmptyWhenPolicyNotFound() {
        PolicyViewRepository repository = repositoryReturning(null);
        PolicyCashValuePort port = cashValuePortReturning(sampleCashValue(1));

        assertTrue(service(repository, port).query(TENANT_ID, POLICY_ID, LocalDate.of(2026, 6, 1)).isEmpty());
        verifyNoInteractions(port);
    }

    @Test
    @DisplayName("产品ID缺失：返回空且不发起远程取数（保单为无产品出单）")
    void shouldReturnEmptyWhenProductIdMissing() {
        PolicyView view = policyView();
        view.setProductId(null);
        PolicyCashValuePort port = cashValuePortReturning(sampleCashValue(1));

        assertTrue(service(repositoryReturning(view), port).query(TENANT_ID, POLICY_ID, LocalDate.of(2026, 6, 1))
                .isEmpty());
        verifyNoInteractions(port);
    }

    @Test
    @DisplayName("总保费缺失：返回空且不发起远程取数（无计算基数）")
    void shouldReturnEmptyWhenTotalPremiumMissing() {
        PolicyView view = policyView();
        view.setTotalPremium(null);
        PolicyCashValuePort port = cashValuePortReturning(sampleCashValue(1));

        assertTrue(service(repositoryReturning(view), port).query(TENANT_ID, POLICY_ID, LocalDate.of(2026, 6, 1))
                .isEmpty());
        verifyNoInteractions(port);
    }

    @Test
    @DisplayName("生效日缺失：返回空且不发起远程取数（无保单年度基准）")
    void shouldReturnEmptyWhenStartDateMissing() {
        PolicyView view = policyView();
        view.setStartDate(null);
        PolicyCashValuePort port = cashValuePortReturning(sampleCashValue(1));

        assertTrue(service(repositoryReturning(view), port).query(TENANT_ID, POLICY_ID, LocalDate.of(2026, 6, 1))
                .isEmpty());
        verifyNoInteractions(port);
    }

    @Test
    @DisplayName("产品域无适用策略：向上返回空，不伪造金额")
    void shouldReturnEmptyWhenProductHasNoPolicy() {
        PolicyViewRepository repository = repositoryReturning(policyView());
        PolicyCashValuePort port = cashValuePortReturning(null);

        assertTrue(service(repository, port).query(TENANT_ID, POLICY_ID, LocalDate.of(2026, 6, 1)).isEmpty());
        verify(port).getCashValue(anyString(), anyString(), anyInt(), any(), any(), any());
    }
}
