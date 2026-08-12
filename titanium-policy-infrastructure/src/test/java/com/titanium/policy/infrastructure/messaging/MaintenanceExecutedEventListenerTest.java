package com.titanium.policy.infrastructure.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.titanium.policy.application.orchestration.maintenance.strategy.GenericEndorsementWriteBackStrategy;
import com.titanium.policy.application.orchestration.maintenance.strategy.ReinstatePolicyWriteBackStrategy;
import com.titanium.policy.application.orchestration.maintenance.strategy.ResumePolicyWriteBackStrategy;
import com.titanium.policy.application.orchestration.maintenance.strategy.SuspendPolicyWriteBackStrategy;
import com.titanium.policy.application.orchestration.maintenance.strategy.TerminatePolicyWriteBackStrategy;
import com.titanium.policy.command.ApplyPolicyEndorsementCommand;
import com.titanium.policy.command.ReinstatePolicyCommand;
import com.titanium.policy.command.ResumePolicyCommand;
import com.titanium.policy.command.SuspendPolicyCommand;
import com.titanium.policy.command.TerminatePolicyCommand;
import com.titanium.policy.common.enums.PolicyDataUpdateType;

/**
 * 保全执行事件监听器测试（infra 入站适配器）
 * <p>
 * 验证保全→保单回写闭环的核心：JSON 解析 → 按保全类型策略分发（application 策略）→ 构造正确的保单命令。
 * </p>
 */
class MaintenanceExecutedEventListenerTest {

    private CommandGateway                   commandGateway;
    private MaintenanceExecutedEventListener listener;

    @BeforeEach
    void setUp() {
        commandGateway = Mockito.mock(CommandGateway.class);
        when(commandGateway.sendAndWait(any())).thenReturn(null);
        listener = new MaintenanceExecutedEventListener(List.of(
                new SuspendPolicyWriteBackStrategy(commandGateway),
                new ResumePolicyWriteBackStrategy(commandGateway),
                new TerminatePolicyWriteBackStrategy(commandGateway),
                new ReinstatePolicyWriteBackStrategy(commandGateway),
                new GenericEndorsementWriteBackStrategy(commandGateway, policyId -> "ED-1")));
    }

    private String payload(String maintenanceType) {
        return String.format(
                "{\"maintenanceId\":{\"id\":\"M-1\"},\"policyId\":\"POL-1\",\"maintenanceType\":\"%s\","
                        + "\"executionDetails\":\"客户申请\",\"updatedBy\":\"op-1\",\"tenantId\":\"t-1\"}",
                maintenanceType);
    }

    @Test
    void shouldDispatchSuspendCommand() {
        listener.onMaintenanceExecuted(payload("POLICY_SUSPENSION"));
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway).sendAndWait(captor.capture());
        SuspendPolicyCommand cmd = (SuspendPolicyCommand) captor.getValue();
        assertEquals("POL-1", cmd.policyId());
        assertEquals("op-1", cmd.operatorId());
        assertEquals("t-1", cmd.tenantId());
    }

    @Test
    void shouldDispatchResumeCommand() {
        listener.onMaintenanceExecuted(payload("POLICY_RESUMPTION"));
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway).sendAndWait(captor.capture());
        assertEquals("POL-1", ((ResumePolicyCommand) captor.getValue()).policyId());
    }

    @Test
    void shouldDispatchTerminateCommandWithWithdrawalReason() {
        listener.onMaintenanceExecuted(payload("POLICY_TERMINATION"));
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway).sendAndWait(captor.capture());
        TerminatePolicyCommand cmd = (TerminatePolicyCommand) captor.getValue();
        assertEquals("POL-1", cmd.policyId());
        assertEquals("WITHDRAWAL", cmd.terminationReason().name());
    }

    @Test
    void shouldDispatchReinstateCommand() {
        listener.onMaintenanceExecuted(payload("POLICY_REINSTATEMENT"));
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway).sendAndWait(captor.capture());
        assertEquals("POL-1", ((ReinstatePolicyCommand) captor.getValue()).policyId());
    }

    @Test
    void shouldDispatchEndorsementForDataChange() {
        // 数据/要素类保全（受益人变更）回退到通用批改策略，下发 ApplyPolicyEndorsementCommand
        listener.onMaintenanceExecuted(payload("BENEFICIARY_CHANGE"));
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway).sendAndWait(captor.capture());
        ApplyPolicyEndorsementCommand cmd = (ApplyPolicyEndorsementCommand) captor.getValue();
        assertEquals("POL-1", cmd.policyId());
        assertEquals("ED-1", cmd.endorsementNo());
        assertEquals(PolicyDataUpdateType.BENEFICIARY_CHANGE, cmd.updateType());
        assertEquals("M-1", cmd.sourceMaintenanceId());
    }

    @Test
    void shouldSkipUnknownMaintenanceType() {
        // 既非状态类也非可批改类型，不下发任何命令
        listener.onMaintenanceExecuted(payload("SOME_UNKNOWN_TYPE"));
        verifyNoInteractions(commandGateway);
    }

    @Test
    void shouldSkipWhenPolicyIdMissing() {
        listener.onMaintenanceExecuted(
                "{\"maintenanceType\":\"POLICY_SUSPENSION\",\"updatedBy\":\"op-1\",\"tenantId\":\"t-1\"}");
        verify(commandGateway, never()).sendAndWait(any());
    }
}
