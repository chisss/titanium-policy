package com.titanium.policy.web.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.request.RecordPremiumCollectionRequest;
import com.titanium.policy.api.request.maintenance.ApplyPolicyMaintenanceRequest;
import com.titanium.policy.api.request.maintenance.PolicyMaintenanceFieldChangeRequest;
import com.titanium.policy.api.response.maintenance.PolicyMaintenanceApplicationResponse;
import com.titanium.policy.application.command.PolicyApplicationService;
import com.titanium.policy.application.query.PolicyAppQueryService;
import com.titanium.policy.command.ApplyPolicyMaintenanceCommand;
import com.titanium.policy.command.RecordPremiumCollectionCommand;
import com.titanium.policy.query.result.PolicyEndorsementQueryResult;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceApplicationReceipt;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceAppliedField;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceSnapshotReference;
import com.titanium.policy.web.mapper.PolicyWebMapper;

class PolicyApiProviderTest {

    @Test
    void shouldMapPremiumCollectionToTenantScopedCommand() {
        PolicyApplicationService applicationService = mock(PolicyApplicationService.class);
        PolicyApiProvider provider = new PolicyApiProvider(
                applicationService, mock(PolicyAppQueryService.class), mock(PolicyWebMapper.class));
        LocalDateTime collectedTime = LocalDateTime.of(2026, 8, 26, 16, 53);
        RecordPremiumCollectionRequest request = new RecordPremiumCollectionRequest(
                "payment-1", "trade-1", new BigDecimal("121.20"), "CNY", "ANNUAL", collectedTime);

        ApiResponse<Void> response = provider.recordPremiumCollection(
                "policy-1", request, "payment-callback", "tenant-1");

        ArgumentCaptor<RecordPremiumCollectionCommand> commandCaptor =
                ArgumentCaptor.forClass(RecordPremiumCollectionCommand.class);
        verify(applicationService).recordPremiumCollection(commandCaptor.capture());
        RecordPremiumCollectionCommand command = commandCaptor.getValue();
        assertEquals("policy-1", command.policyId());
        assertEquals("payment-1", command.paymentId());
        assertEquals("trade-1", command.paymentNo());
        assertEquals(new BigDecimal("121.20"), command.collectedAmount().value());
        assertEquals("CNY", command.collectedAmount().currency());
        assertEquals("ANNUAL", command.paymentMethod().getCode());
        assertEquals(collectedTime, command.collectedTime());
        assertEquals("payment-callback", command.operatorId());
        assertEquals("tenant-1", command.tenantId());
        assertTrue(response.isSuccess());
    }

    @Test
    void shouldExposeTenantScopedEndorsements() {
        PolicyAppQueryService queryService = mock(PolicyAppQueryService.class);
        PolicyApiProvider provider = new PolicyApiProvider(
                mock(PolicyApplicationService.class), queryService, mock(PolicyWebMapper.class));
        PolicyEndorsementQueryResult endorsement = new PolicyEndorsementQueryResult();
        endorsement.setEndorsementNo("endorsement-1");
        endorsement.setPolicyId("policy-1");
        endorsement.setUpdateType("MAINTENANCE");
        endorsement.setCategory("DATA");
        endorsement.setPolicyVersion(8);
        endorsement.setEffectiveDate(LocalDateTime.of(2026, 8, 20, 0, 0));
        endorsement.setChangeSummary("变更联系地址");
        endorsement.setSourceMaintenanceId("case-1");
        endorsement.setEndorsedAt(LocalDateTime.of(2026, 8, 20, 9, 0));
        endorsement.setTenantId("tenant-1");
        when(queryService.findEndorsements("policy-1", "tenant-1")).thenReturn(List.of(endorsement));

        var response = provider.getEndorsements("policy-1", "tenant-1");

        verify(queryService).findEndorsements("policy-1", "tenant-1");
        assertTrue(response.isSuccess());
        assertEquals("endorsement-1", response.getData().getFirst().endorsementNo());
        assertEquals("tenant-1", response.getData().getFirst().tenantId());
    }

    @Test
    void shouldMapMaintenanceRequestAndReturnAuthoritativeReceipt() {
        PolicyApplicationService applicationService = mock(PolicyApplicationService.class);
        PolicyApiProvider provider = new PolicyApiProvider(
                applicationService, mock(PolicyAppQueryService.class), mock(PolicyWebMapper.class));
        LocalDateTime effectiveAt = LocalDateTime.of(2026, 8, 25, 10, 30);
        LocalDateTime appliedAt = LocalDateTime.of(2026, 8, 25, 10, 31);
        OffsetDateTime capturedAt = OffsetDateTime.parse("2026-08-25T10:31:00+08:00");
        String requestHash = "a".repeat(64);
        String proposedSnapshotHash = "b".repeat(64);
        String applicationHash = "c".repeat(64);
        ApplyPolicyMaintenanceRequest request = new ApplyPolicyMaintenanceRequest(
                "request-1", "case-1", 7L, requestHash, proposedSnapshotHash,
                "IMMEDIATE", effectiveAt, "变更投保人手机号",
                List.of(new PolicyMaintenanceFieldChangeRequest(
                        "POLICY_BASIC_INFO_CHANGE", "holder-1", "policy.holder.mobile", "STRING", "13800138000")));
        PolicyMaintenanceApplicationReceipt receipt = new PolicyMaintenanceApplicationReceipt(
                "request-1", requestHash, "ENDORSEMENT-1", 7L, 8L, applicationHash,
                new PolicyMaintenanceSnapshotReference(
                        "policy/policy-1/version/8", applicationHash, 8L, capturedAt),
                List.of(new PolicyMaintenanceAppliedField(
                        "POLICY_BASIC_INFO_CHANGE", "holder-1", "policy.holder.mobile", "STRING", "13800138000")),
                appliedAt);
        when(applicationService.applyMaintenance(any())).thenReturn(receipt);

        ApiResponse<PolicyMaintenanceApplicationResponse> response =
                provider.applyMaintenance("policy-1", request, "operator-1", "tenant-1");

        ArgumentCaptor<ApplyPolicyMaintenanceCommand> commandCaptor =
                ArgumentCaptor.forClass(ApplyPolicyMaintenanceCommand.class);
        verify(applicationService).applyMaintenance(commandCaptor.capture());
        ApplyPolicyMaintenanceCommand command = commandCaptor.getValue();
        assertEquals("policy-1", command.policyId());
        assertEquals("request-1", command.requestId());
        assertEquals("case-1", command.sourceMaintenanceId());
        assertEquals(7L, command.expectedPolicyVersion());
        assertEquals(requestHash, command.requestPayloadHash());
        assertEquals(proposedSnapshotHash, command.proposedSnapshotHash());
        assertEquals("IMMEDIATE", command.effectiveTimeType());
        assertEquals(effectiveAt, command.effectiveAt());
        assertEquals("变更投保人手机号", command.changeSummary());
        assertEquals("operator-1", command.operatorId());
        assertEquals("tenant-1", command.tenantId());
        assertEquals("policy.holder.mobile", command.changes().getFirst().fieldCode());
        assertEquals("13800138000", command.changes().getFirst().canonicalValue());

        assertTrue(response.isSuccess());
        assertEquals("ENDORSEMENT-1", response.getData().endorsementNo());
        assertEquals(8L, response.getData().actualPolicyVersion());
        assertEquals(applicationHash, response.getData().applicationHash());
        assertEquals("policy/policy-1/version/8", response.getData().appliedSnapshot().storageKey());
        assertEquals(capturedAt, response.getData().appliedSnapshot().capturedAt());
        assertEquals("policy.holder.mobile", response.getData().appliedFields().getFirst().fieldCode());
        assertEquals("13800138000", response.getData().appliedFields().getFirst().canonicalValue());
        assertEquals(appliedAt, response.getData().appliedAt());
    }
}
