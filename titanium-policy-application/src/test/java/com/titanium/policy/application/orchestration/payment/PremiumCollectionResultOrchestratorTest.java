package com.titanium.policy.application.orchestration.payment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.command.ActivatePolicyCommand;
import com.titanium.policy.command.RecordPremiumCollectionCommand;
import com.titanium.policy.common.constant.PolicyConstants;
import com.titanium.policy.valueobject.payment.PremiumCollectionNotice;

/**
 * 保费实收回写编排用例
 * <p>
 * 锁死此前断裂的链路：{@code RecordPremiumCollectionCommand} 的处理器虽已存在，但全库零调用方，
 * 线上/代扣/线下收费的保单永远停在未生效态。本用例验证支付成功事实能经本编排器落成
 * 「实收写入 + 生效驱动」两条命令。
 * </p>
 */
class PremiumCollectionResultOrchestratorTest {

    private static final String POLICY_ID = "POLICY-001";
    private static final String TENANT_ID = "TENANT-001";

    private CommandGateway commandGateway;
    private PremiumCollectionResultOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        commandGateway = Mockito.mock(CommandGateway.class);
        Mockito.when(commandGateway.sendAndWait(any())).thenReturn(null);
        orchestrator = new PremiumCollectionResultOrchestrator(commandGateway);
    }

    /**
     * 实收回写与生效驱动缺一不可：只写实收则保单仍停未生效，只驱动生效则被聚合的保费条件拒绝。
     */
    @Test
    void shouldRecordCollectionThenActivatePolicy() {
        orchestrator.onPaymentSucceeded(notice());

        ArgumentCaptor<RecordPremiumCollectionCommand> recorded =
                ArgumentCaptor.forClass(RecordPremiumCollectionCommand.class);
        verify(commandGateway).sendAndWait(recorded.capture());
        assertEquals(POLICY_ID, recorded.getValue().policyId());
        assertEquals("PAY-001", recorded.getValue().paymentId());
        assertEquals("PAY-NO-001", recorded.getValue().paymentNo());
        assertEquals(0, new BigDecimal("8888.00").compareTo(recorded.getValue().collectedAmount().value()));
        assertEquals(TENANT_ID, recorded.getValue().tenantId());
        assertEquals(PolicyConstants.POLICY_SYSTEM, recorded.getValue().operatorId(),
                "跨域回调无人工操作人，须落系统标识");

        verify(commandGateway).sendAndWait(new ActivatePolicyCommand(POLICY_ID, TENANT_ID));
    }

    /**
     * 生效失败不回滚实收：实收是已发生的资金事实，保障起期未到属正常，由定时激活任务补齐。
     * 抛异常会让同一条消息被无限重放而结果不变。
     */
    @Test
    void shouldNotPropagateActivationRejection() {
        Mockito.when(commandGateway.sendAndWait(any(ActivatePolicyCommand.class)))
                .thenThrow(new IllegalStateException("Insurance period has not started yet"));

        assertDoesNotThrow(() -> orchestrator.onPaymentSucceeded(notice()));

        verify(commandGateway).sendAndWait(any(RecordPremiumCollectionCommand.class));
    }

    private PremiumCollectionNotice notice() {
        return new PremiumCollectionNotice(POLICY_ID, "PAY-001", "PAY-NO-001",
                Money.of(new BigDecimal("8888.00"), "CNY"), LocalDateTime.of(2026, 9, 11, 10, 0), TENANT_ID);
    }
}
