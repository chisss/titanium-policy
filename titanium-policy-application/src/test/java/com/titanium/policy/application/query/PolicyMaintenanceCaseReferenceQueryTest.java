package com.titanium.policy.application.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import com.titanium.policy.query.query.FindEndorsementsByPolicyIdQuery;
import com.titanium.policy.query.result.PolicyEndorsementQueryResult;
import com.titanium.policy.query.result.PolicyMaintenanceCaseReferenceQueryResult;
import com.titanium.policy.query.service.PolicyQueryService;

class PolicyMaintenanceCaseReferenceQueryTest {

    @Test
    void shouldFilterBlankReferencesDeduplicateCasesAndPreserveTenantScope() {
        QueryGateway queryGateway = mock(QueryGateway.class);
        PolicyQueryService policyQueryService = mock(PolicyQueryService.class);
        List<PolicyEndorsementQueryResult> endorsements = List.of(
                endorsement("endorsement-1", "maintenance-1", 2),
                endorsement("endorsement-2", " ", 3),
                endorsement("endorsement-3", "maintenance-1", 4),
                endorsement("endorsement-4", "maintenance-2", 5));
        when(queryGateway.query(
                any(FindEndorsementsByPolicyIdQuery.class),
                ArgumentMatchers.<ResponseType<List<PolicyEndorsementQueryResult>>>any()))
                .thenReturn(CompletableFuture.completedFuture(endorsements));

        List<PolicyMaintenanceCaseReferenceQueryResult> result =
                new PolicyAppQueryService(queryGateway, policyQueryService)
                        .findMaintenanceCaseReferences("policy-1", "tenant-1");

        assertEquals(List.of("maintenance-1", "maintenance-2"), result.stream()
                .map(PolicyMaintenanceCaseReferenceQueryResult::maintenanceId)
                .toList());
        assertEquals("endorsement-1", result.getFirst().endorsementNo());
        assertEquals(2, result.getFirst().policyVersion());
        ArgumentCaptor<FindEndorsementsByPolicyIdQuery> queryCaptor =
                ArgumentCaptor.forClass(FindEndorsementsByPolicyIdQuery.class);
        verify(queryGateway).query(
                queryCaptor.capture(),
                ArgumentMatchers.<ResponseType<List<PolicyEndorsementQueryResult>>>any());
        assertEquals("policy-1", queryCaptor.getValue().policyId());
        assertEquals("tenant-1", queryCaptor.getValue().tenantId());
    }

    private PolicyEndorsementQueryResult endorsement(String endorsementNo, String maintenanceId, int policyVersion) {
        PolicyEndorsementQueryResult result = new PolicyEndorsementQueryResult();
        result.setEndorsementNo(endorsementNo);
        result.setSourceMaintenanceId(maintenanceId);
        result.setUpdateType("POLICY_INFO_CHANGE");
        result.setPolicyVersion(policyVersion);
        result.setEffectiveDate(LocalDateTime.parse("2026-08-25T09:00:00"));
        result.setEndorsedAt(LocalDateTime.parse("2026-08-25T10:00:00"));
        result.setTenantId("tenant-1");
        return result;
    }
}
