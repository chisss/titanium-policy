package com.titanium.policy.application.orchestration.issuance.orchestrator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.metadata.enums.billing.PremiumCollectionMode;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.command.AssociatePremiumBillingCommand;
import com.titanium.policy.command.RecordPremiumCollectionCommand;
import com.titanium.policy.common.enums.PremiumCollectionStatus;
import com.titanium.policy.port.BillingServicePort;
import com.titanium.policy.port.PaymentServicePort;
import com.titanium.policy.valueobject.billing.BillingResult;
import com.titanium.policy.valueobject.billing.PremiumBillRequest;
import com.titanium.policy.valueobject.payment.PaymentOrderResult;
import com.titanium.policy.valueobject.policy.CollectionResult;

/**
 * 收费编排的账单、支付单与保单关联顺序测试。
 */
class PremiumCollectionOrchestratorTest {

    private static final String POLICY_ID = "POLICY_001";
    private static final String TENANT_ID = "TENANT_001";
    private static final Money PREMIUM = Money.of(new BigDecimal("1000.00"), "CNY");

    private BillingServicePort billingServicePort;
    private PaymentServicePort paymentServicePort;
    private CommandGateway commandGateway;
    private PremiumCollectionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        billingServicePort = mock(BillingServicePort.class);
        paymentServicePort = mock(PaymentServicePort.class);
        commandGateway = mock(CommandGateway.class);
        orchestrator = new PremiumCollectionOrchestrator(billingServicePort, paymentServicePort, commandGateway);
    }

    @Test
    void billingFailureDoesNotCreateOrphanPaymentOrder() {
        when(billingServicePort.createPremiumBill(any())).thenReturn(new BillingResult(false, null));

        CollectionResult result = orchestrator.collect(POLICY_ID, "CUSTOMER_001", PREMIUM,
                PremiumCollectionMode.ONLINE, TENANT_ID);

        assertNull(result.billId());
        assertEquals("开立保费账单失败", result.skipReason());
        verifyNoInteractions(paymentServicePort, commandGateway);
    }

    @Test
    void onlinePaymentDocumentsAreAssociatedWithPolicy() {
        when(billingServicePort.createPremiumBill(any()))
                .thenReturn(new BillingResult(true, "BILL_001", "ACCOUNT_001"));
        when(paymentServicePort.createPaymentOrder(any()))
                .thenReturn(PaymentOrderResult.succeeded("PAY_001", PREMIUM, "PENDING", "credential"));

        LocalDate dueDate = LocalDate.of(2026, 9, 1);
        CollectionResult result = orchestrator.collect(POLICY_ID, "CUSTOMER_001", PREMIUM,
                PremiumCollectionMode.ONLINE, dueDate, TENANT_ID);

        assertEquals(PremiumCollectionStatus.UNCOLLECTED, result.status());
        assertEquals("BILL_001", result.billId());
        assertEquals("ACCOUNT_001", result.billingAccountId());
        assertEquals("PAY_001", result.paymentOrderId());
        ArgumentCaptor<PremiumBillRequest> billCaptor = ArgumentCaptor.forClass(PremiumBillRequest.class);
        verify(billingServicePort).createPremiumBill(billCaptor.capture());
        assertEquals(dueDate, billCaptor.getValue().dueDate());
        verify(commandGateway).sendAndWait(
                new AssociatePremiumBillingCommand(POLICY_ID, "BILL_001", null, TENANT_ID));
        verify(commandGateway).sendAndWait(
                new AssociatePremiumBillingCommand(POLICY_ID, "BILL_001", "PAY_001", TENANT_ID));
    }

    @Test
    void paymentExceptionReturnsPendingBillForRetry() {
        when(billingServicePort.createPremiumBill(any()))
                .thenReturn(new BillingResult(true, "BILL_001", "ACCOUNT_001"));
        when(paymentServicePort.createPaymentOrder(any())).thenThrow(new IllegalStateException("payment unavailable"));

        CollectionResult result = orchestrator.collect(POLICY_ID, "CUSTOMER_001", PREMIUM,
                PremiumCollectionMode.ONLINE, TENANT_ID);

        assertEquals(PremiumCollectionStatus.UNCOLLECTED, result.status());
        assertEquals("BILL_001", result.billId());
        assertNull(result.paymentOrderId());
        verify(commandGateway).sendAndWait(
                new AssociatePremiumBillingCommand(POLICY_ID, "BILL_001", null, TENANT_ID));
    }

    @Test
    void freeModeAssociatesBillWithoutFakeCollectionRecord() {
        when(billingServicePort.createPremiumBill(any()))
                .thenReturn(new BillingResult(true, "BILL_FREE", "ACCOUNT_FREE"));

        CollectionResult result = orchestrator.collect(POLICY_ID, "CUSTOMER_001", Money.zero("CNY"),
                PremiumCollectionMode.FREE, TENANT_ID);

        assertEquals(PremiumCollectionStatus.COLLECTED, result.status());
        verify(commandGateway).sendAndWait(
                new AssociatePremiumBillingCommand(POLICY_ID, "BILL_FREE", null, TENANT_ID));
        verify(commandGateway, never()).sendAndWait(any(RecordPremiumCollectionCommand.class));
        verifyNoInteractions(paymentServicePort);
    }
}
