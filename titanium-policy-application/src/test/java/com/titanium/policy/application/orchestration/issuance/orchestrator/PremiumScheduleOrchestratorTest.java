package com.titanium.policy.application.orchestration.issuance.orchestrator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.port.BillingServicePort;
import com.titanium.policy.valueobject.billing.PremiumScheduleRequest;

class PremiumScheduleOrchestratorTest {

    private static final String POLICY_ID = "POLICY_001";
    private static final String BILL_ID = "BILL_001";
    private static final String ACCOUNT_ID = "ACCOUNT_001";
    private static final String TENANT_ID = "TENANT_001";
    private static final LocalDate FIRST_DUE_DATE = LocalDate.of(2026, 8, 13);

    private BillingServicePort billingServicePort;
    private PremiumScheduleOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        billingServicePort = mock(BillingServicePort.class);
        orchestrator = new PremiumScheduleOrchestrator(billingServicePort);
    }

    @ParameterizedTest
    @CsvSource({
        "LUMP_SUM,1,1000.00",
        "ANNUAL,20,50.00",
        "SEMI_ANNUAL,40,25.00",
        "QUARTERLY,80,12.50",
        "MONTHLY,240,4.17"
    })
    void calculatesAllSupportedPaymentFrequencies(String paymentMode, int expectedPeriods,
                                                   BigDecimal expectedInstallment) {
        orchestrator.generate(POLICY_ID, BILL_ID, ACCOUNT_ID, paymentMode, 20,
                Money.of(new BigDecimal("1000.00"), "CNY"), FIRST_DUE_DATE, TENANT_ID);

        ArgumentCaptor<PremiumScheduleRequest> captor = ArgumentCaptor.forClass(PremiumScheduleRequest.class);
        verify(billingServicePort).generatePremiumSchedule(captor.capture());
        PremiumScheduleRequest request = captor.getValue();
        assertEquals(ACCOUNT_ID, request.billingAccountId());
        assertEquals(expectedPeriods, request.totalPeriods());
        assertEquals(expectedInstallment, request.installmentAmount());
        assertEquals(FIRST_DUE_DATE, request.firstDueDate());
    }

    @Test
    void skipsScheduleWithoutSynchronousBillingAccountId() {
        orchestrator.generate(POLICY_ID, BILL_ID, null, "ANNUAL", 20,
                Money.of(new BigDecimal("1000.00"), "CNY"), FIRST_DUE_DATE, TENANT_ID);

        verify(billingServicePort, never()).generatePremiumSchedule(any());
    }

    @Test
    void remoteScheduleFailureDoesNotEscapeIssuanceFlow() {
        doThrow(new IllegalStateException("billing unavailable"))
                .when(billingServicePort).generatePremiumSchedule(any());

        assertDoesNotThrow(() -> orchestrator.generate(POLICY_ID, BILL_ID, ACCOUNT_ID, "ANNUAL", 20,
                Money.of(new BigDecimal("1000.00"), "CNY"), FIRST_DUE_DATE, TENANT_ID));
    }
}
