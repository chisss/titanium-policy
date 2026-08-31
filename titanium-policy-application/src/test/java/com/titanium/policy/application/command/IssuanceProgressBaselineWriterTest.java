package com.titanium.policy.application.command;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.billing.PremiumCollectionMode;
import com.titanium.metadata.enums.policy.IssuanceStrategy;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.policy.common.enums.IssuanceStage;
import com.titanium.policy.query.repository.IssuanceProgressViewRepository;
import com.titanium.policy.query.view.IssuanceProgressView;
import com.titanium.policy.valueobject.IssuanceRequest;
import com.titanium.policy.valueobject.IssuanceResult;
import com.titanium.policy.valueobject.RuleDecision;

class IssuanceProgressBaselineWriterTest {

    @Test
    void flushesBaselineSoConcurrentUniqueConflictIsVisibleToCaller() {
        IssuanceProgressViewRepository repository = mock(IssuanceProgressViewRepository.class);
        IssuanceProgressBaselineWriter writer = new IssuanceProgressBaselineWriter(repository);
        IssuanceRequest request = request();
        IssuanceResult result = new IssuanceResult(true, request.bizNo(), null, request.issuanceStrategy(),
                IssuanceStage.ACCEPTED, null, null, null, null, List.of(), null, null, null, null, null, null, null,
                null, null);

        writer.save(request, result);

        verify(repository).saveAndFlush(any(IssuanceProgressView.class));
    }

    @Test
    void marksOnlyUntouchedAcceptedBaselineRejected() {
        IssuanceProgressViewRepository repository = mock(IssuanceProgressViewRepository.class);
        IssuanceProgressBaselineWriter writer = new IssuanceProgressBaselineWriter(repository);
        IssuanceRequest request = request();
        IssuanceResult rejected = IssuanceResult.rejected(request.bizNo(),
                RuleDecision.rejected(PolicyErrorCode.ISSUANCE_RISK_REJECTED, "基础自动核保"));
        when(repository.markUntouchedBaselineRejected(eq("BIZ_001"), eq("TENANT_001"), eq("ACCEPTED"),
                eq("REJECTED"), eq("20007010"), eq("出单风控步骤 基础自动核保 不通过"), any(LocalDateTime.class)))
                .thenReturn(1);

        boolean updated = writer.markRejectedIfUntouched(request, rejected);

        assertTrue(updated);
        verify(repository).markUntouchedBaselineRejected(eq("BIZ_001"), eq("TENANT_001"), eq("ACCEPTED"),
                eq("REJECTED"), eq("20007010"), eq("出单风控步骤 基础自动核保 不通过"), any(LocalDateTime.class));
    }

    @Test
    void releasesOnlyUntouchedAcceptedBaseline() {
        IssuanceProgressViewRepository repository = mock(IssuanceProgressViewRepository.class);
        IssuanceProgressBaselineWriter writer = new IssuanceProgressBaselineWriter(repository);
        IssuanceRequest request = request();
        when(repository.deleteUntouchedAcceptedBaseline("BIZ_001", "TENANT_001", "ACCEPTED")).thenReturn(1);

        boolean released = writer.releaseIfUntouched(request);

        assertTrue(released);
        verify(repository).deleteUntouchedAcceptedBaseline("BIZ_001", "TENANT_001", "ACCEPTED");
    }

    private IssuanceRequest request() {
        return new IssuanceRequest("BIZ_001", "TENANT_001", "USER_001", null,
                IssuanceStrategy.MERGE_ONE_POLICY, "CUSTOMER_001", null, PolicyForm.INDIVIDUAL, null,
                LocalDateTime.now(), LocalDateTime.now().plusYears(1), PremiumCollectionMode.ONLINE, null, null, null,
                List.of(), null, null);
    }
}
