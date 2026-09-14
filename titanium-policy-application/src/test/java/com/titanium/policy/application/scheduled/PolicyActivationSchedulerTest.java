package com.titanium.policy.application.scheduled;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;

import com.titanium.metadata.enums.policy.PolicyEnum;
import com.titanium.policy.application.command.policy.PolicyApplicationService;
import com.titanium.policy.query.repository.PolicyViewRepository;
import com.titanium.policy.query.view.PolicyView;

/**
 * 待生效保单定时激活补偿用例
 * <p>
 * 锁死此前断裂的链路：支付回调到达时若保障起期未到，即时激活必被聚合拒绝；而出单到起期之间
 * 相隔数日是标准场景（今日投保、次日起保），此前三处 javadoc 均称「由定时激活任务补齐」，
 * 该任务在全仓并不存在——保单因此永远停在待生效态。本用例验证补偿通道真实存在并正确取数。
 * </p>
 */
class PolicyActivationSchedulerTest {

    private static final String TENANT_A = "TENANT-A";
    private static final String TENANT_B = "TENANT-B";

    private PolicyViewRepository     policyViewRepository;
    private PolicyApplicationService policyApplicationService;
    private PolicyActivationScheduler scheduler;

    @BeforeEach
    void setUp() {
        policyViewRepository = Mockito.mock(PolicyViewRepository.class);
        policyApplicationService = Mockito.mock(PolicyApplicationService.class);
        scheduler = new PolicyActivationScheduler(policyViewRepository, policyApplicationService);
    }

    /**
     * 起期已到的待生效保单须被逐条激活，且各自携带自身租户——跨租户批处理不得错用租户上下文。
     */
    @Test
    void shouldActivateDuePoliciesWithOwnTenant() {
        stubFirstPage(policy("POLICY-A", TENANT_A), policy("POLICY-B", TENANT_B));

        scheduler.activateDuePolicies();

        verify(policyApplicationService).activatePolicy("POLICY-A", TENANT_A);
        verify(policyApplicationService).activatePolicy("POLICY-B", TENANT_B);
    }

    /**
     * 取数口径：只捞「待生效 + 起期已到」，且平台不得在查询层过滤保费条件——
     * 能否真生效由聚合 {@code canActivate()} 统一判定，读侧复制一份规则会与写侧漂移。
     */
    @Test
    void shouldQueryPendingEffectivePoliciesStartedBeforeNow() {
        when(policyViewRepository.findByPolicyStatusAndStartDateLessThanEqual(any(), any(), any()))
                .thenReturn(List.of());

        scheduler.activateDuePolicies();

        ArgumentCaptor<PolicyEnum.PolicyStatus> statusCaptor = ArgumentCaptor.forClass(PolicyEnum.PolicyStatus.class);
        ArgumentCaptor<LocalDateTime> dueCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(policyViewRepository).findByPolicyStatusAndStartDateLessThanEqual(statusCaptor.capture(),
                dueCaptor.capture(), any(Pageable.class));
        assertEquals(PolicyEnum.PolicyStatus.PENDING_EFFECTIVE,
                statusCaptor.getValue(), "写侧 NOT_EFFECTIVE 在读模型的映射值是 PENDING_EFFECTIVE，勿混用");
        assertTrue(
                dueCaptor.getValue().isBefore(LocalDateTime.now().plusSeconds(1)), "基准日应为当前时刻，起期已到才入选");
    }

    /**
     * 错误隔离：单条激活失败（多为保费未收讫，属预期）不得中断批次，其余保单照常处理。
     */
    @Test
    void shouldIsolateSingleFailureAndContinueBatch() {
        stubFirstPage(policy("POLICY-A", TENANT_A), policy("POLICY-B", TENANT_B));
        doThrow(new IllegalStateException("首期保费未收讫")).when(policyApplicationService).activatePolicy("POLICY-A",
                TENANT_A);

        scheduler.activateDuePolicies();

        verify(policyApplicationService).activatePolicy("POLICY-B", TENANT_B);
    }

    /**
     * 空结果即终止，不得空转翻页。
     */
    @Test
    void shouldStopWhenNoDuePolicy() {
        when(policyViewRepository.findByPolicyStatusAndStartDateLessThanEqual(any(), any(), any()))
                .thenReturn(List.of());

        scheduler.activateDuePolicies();

        verify(policyApplicationService, never()).activatePolicy(any(), any());
    }

    /** 首页返回不足一页（含两条）即终止翻页，故仓储只应被查询一次。 */
    private void stubFirstPage(PolicyView... policies) {
        when(policyViewRepository.findByPolicyStatusAndStartDateLessThanEqual(
                eq(PolicyEnum.PolicyStatus.PENDING_EFFECTIVE), any(), any())).thenReturn(List.of(policies));
    }

    private PolicyView policy(String policyId, String tenantId) {
        PolicyView view = Mockito.mock(PolicyView.class);
        when(view.getPolicyId()).thenReturn(policyId);
        when(view.getPolicyNo()).thenReturn(policyId + "-NO");
        when(view.getTenantId()).thenReturn(tenantId);
        return view;
    }
}
