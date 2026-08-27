package com.titanium.policy.query.handler.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.common.enums.IssuanceStage;
import com.titanium.policy.common.enums.PremiumCollectionStatus;
import com.titanium.policy.event.PolicyActivatedEvent;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.event.PremiumBillingAssociatedEvent;
import com.titanium.policy.event.insurance.InsuranceSubmittedForUnderwritingEvent;
import com.titanium.policy.event.insurance.UnderwritingResultReceivedEvent;
import com.titanium.policy.query.repository.IssuanceProgressViewRepository;
import com.titanium.policy.query.view.IssuanceProgressView;

/**
 * 出单进度投影的单调性、拒保终态与历史事件兼容测试。
 */
class IssuanceProgressProjectionEventHandlerTest {

    private static final String BIZ_NO = "BIZ_001";
    private static final String TENANT_ID = "TENANT_001";
    private static final String INSURANCE_ID = "INSURANCE_001";

    private IssuanceProgressViewRepository repository;
    private IssuanceProgressProjectionEventHandler handler;

    @BeforeEach
    void setUp() {
        repository = mock(IssuanceProgressViewRepository.class);
        handler = new IssuanceProgressProjectionEventHandler(repository);
    }

    @Test
    void replayedEarlierStageDoesNotMoveProgressBackward() {
        IssuanceProgressView view = progress(IssuanceStage.POLICY_ISSUED);
        when(repository.findByBizNoAndTenantId(BIZ_NO, TENANT_ID)).thenReturn(Optional.of(view));

        handler.on(submittedEvent(BIZ_NO));

        assertEquals(IssuanceStage.POLICY_ISSUED.getCode(), view.getCurrentStage());
        assertEquals(INSURANCE_ID, view.getInsuranceId());
        verify(repository).save(view);
    }

    @Test
    void underwritingRejectionIsTerminalAndStoresReason() {
        IssuanceProgressView view = progress(IssuanceStage.UNDERWRITING);
        when(repository.findByBizNoAndTenantId(BIZ_NO, TENANT_ID)).thenReturn(Optional.of(view));

        handler.on(new UnderwritingResultReceivedEvent(INSURANCE_ID, "UW_001", ConclusionType.REJECT, "风险过高",
                "UW_USER", LocalDateTime.now(), null, TENANT_ID, null, BIZ_NO));
        handler.on(submittedEvent(BIZ_NO));

        assertEquals(IssuanceStage.REJECTED.getCode(), view.getCurrentStage());
        assertEquals("UNDERWRITING_REJECTED", view.getRejectCode());
        assertEquals("风险过高", view.getRejectReason());
        verify(repository).save(view);
    }

    @Test
    void unifiedIssuanceEventWithoutBaselineFailsForDlqRetry() {
        when(repository.findByBizNoAndTenantId(BIZ_NO, TENANT_ID)).thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> handler.on(submittedEvent(BIZ_NO)));

        assertEquals(true, exception.getMessage().contains("缺少基线"));
        verify(repository, never()).save(any());
    }

    @Test
    void historicalEventWithoutBizNoIsIgnored() {
        handler.on(submittedEvent(null));

        verifyNoInteractions(repository);
    }

    @Test
    void lateRejectionDoesNotReplaceEffectivePolicy() {
        IssuanceProgressView view = progress(IssuanceStage.POLICY_EFFECTIVE);
        when(repository.findByBizNoAndTenantId(BIZ_NO, TENANT_ID)).thenReturn(Optional.of(view));

        handler.on(new UnderwritingResultReceivedEvent(INSURANCE_ID, "UW_001", ConclusionType.REJECT, "迟到拒绝",
                "UW_USER", LocalDateTime.now(), null, TENANT_ID, null, BIZ_NO));

        assertEquals(IssuanceStage.POLICY_EFFECTIVE.getCode(), view.getCurrentStage());
        verify(repository, never()).save(view);
    }

    @Test
    void latePolicyEventDoesNotReplaceRejectedProgress() {
        IssuanceProgressView view = progress(IssuanceStage.REJECTED);
        when(repository.findByBizNoAndTenantId(BIZ_NO, TENANT_ID)).thenReturn(Optional.of(view));

        handler.on(new PolicyActivatedEvent("POLICY_001", INSURANCE_ID, BIZ_NO, LocalDateTime.now(), TENANT_ID));

        assertEquals(IssuanceStage.REJECTED.getCode(), view.getCurrentStage());
        verify(repository, never()).save(view);
    }

    @Test
    void billingAssociationAdvancesIssuedPolicyToPendingCollection() {
        IssuanceProgressView view = progress(IssuanceStage.POLICY_ISSUED);
        when(repository.findByBizNoAndTenantId(BIZ_NO, TENANT_ID)).thenReturn(Optional.of(view));

        handler.on(new PremiumBillingAssociatedEvent("POLICY_001", BIZ_NO, "BILL_001", "PAY_001",
                PremiumCollectionStatus.UNCOLLECTED, TENANT_ID));

        assertEquals(IssuanceStage.PENDING_COLLECTION.getCode(), view.getCurrentStage());
        assertEquals("BILL_001", view.getBillId());
        assertEquals("PAY_001", view.getPaymentOrderId());
        verify(repository).save(view);
    }

    @Test
    void policyCreationProjectsStandardAndPayablePremiumSeparately() {
        IssuanceProgressView view = progress(IssuanceStage.UNDERWRITING);
        when(repository.findByBizNoAndTenantId(BIZ_NO, TENANT_ID)).thenReturn(Optional.of(view));

        handler.on(policyCreatedEvent(Money.of(new BigDecimal("1000.00"), "CNY"),
                Money.of(new BigDecimal("1200.00"), "CNY")));

        assertEquals(new BigDecimal("1000.00"), view.getStandardPremium());
        assertEquals(new BigDecimal("1200.00"), view.getPayablePremium());
        verify(repository).save(view);
    }

    @Test
    void historicalPolicyEventDoesNotEraseKnownStandardPremium() {
        IssuanceProgressView view = progress(IssuanceStage.UNDERWRITING);
        view.setStandardPremium(new BigDecimal("900.00"));
        when(repository.findByBizNoAndTenantId(BIZ_NO, TENANT_ID)).thenReturn(Optional.of(view));

        handler.on(policyCreatedEvent(null, Money.of(new BigDecimal("1000.00"), "CNY")));

        assertEquals(new BigDecimal("900.00"), view.getStandardPremium());
        assertEquals(new BigDecimal("1000.00"), view.getPayablePremium());
        verify(repository).save(view);
    }

    private IssuanceProgressView progress(IssuanceStage stage) {
        IssuanceProgressView view = new IssuanceProgressView();
        view.setId(TENANT_ID + "_" + BIZ_NO);
        view.setBizNo(BIZ_NO);
        view.setTenantId(TENANT_ID);
        view.setCurrentStage(stage.getCode());
        return view;
    }

    private InsuranceSubmittedForUnderwritingEvent submittedEvent(String bizNo) {
        return new InsuranceSubmittedForUnderwritingEvent(INSURANCE_ID, "INS_NO_001", "HOLDER_001", 1,
                new BigDecimal("1000"), "CNY", LocalDateTime.now(), LocalDateTime.now().plusYears(1),
                List.of("P001"), 0, PolicyForm.INDIVIDUAL, TENANT_ID, bizNo);
    }

    private PolicyCreatedEvent policyCreatedEvent(Money standardPremium, Money payablePremium) {
        return new PolicyCreatedEvent("POLICY_001", null, PolicyForm.INDIVIDUAL, "PRODUCT_001", INSURANCE_ID,
                null, "UW_001", BIZ_NO, null, null, standardPremium, payablePremium, null, List.of(), null,
                null, null, null, null, null, TENANT_ID);
    }
}
