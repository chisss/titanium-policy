package com.titanium.policy.application.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.titanium.policy.query.result.InsuranceQueryResult;
import com.titanium.policy.query.result.PolicyQueryResult;
import com.titanium.policy.query.result.ProposalQueryResult;
import com.titanium.policy.query.service.InsuranceQueryService;
import com.titanium.policy.query.service.PolicyQueryService;
import com.titanium.policy.query.service.ProposalQueryService;

class RootQueryPaginationTest {

    private static final String TENANT_ID = "TENANT_001";

    @Test
    void policyPageKeepsTotalAndBypassesAxon() {
        QueryGateway queryGateway = mock(QueryGateway.class);
        PolicyQueryService queryService = mock(PolicyQueryService.class);
        PolicyQueryResult item = new PolicyQueryResult();
        Page<PolicyQueryResult> expected = new PageImpl<>(List.of(item), PageRequest.of(2, 10), 43);
        when(queryService.findPoliciesPageByMultipleConditions("POL", null, null, null, null, null, null, null,
                null, TENANT_ID, 2, 10)).thenReturn(expected);

        Page<PolicyQueryResult> actual = new PolicyAppQueryService(queryGateway, queryService)
                .findPageByConditions("POL", null, null, null, null, TENANT_ID, 2, 10);

        assertEquals(43, actual.getTotalElements());
        assertEquals(2, actual.getNumber());
        assertEquals(List.of(item), actual.getContent());
        verifyNoInteractions(queryGateway);
    }

    @Test
    void insurancePageKeepsTotalAndBypassesAxon() {
        QueryGateway queryGateway = mock(QueryGateway.class);
        InsuranceQueryService queryService = mock(InsuranceQueryService.class);
        InsuranceQueryResult item = new InsuranceQueryResult();
        Page<InsuranceQueryResult> expected = new PageImpl<>(List.of(item), PageRequest.of(1, 20), 47);
        when(queryService.findInsurancesPageByConditions("INS", "CUSTOMER_001", "PRODUCT_001", null,
                TENANT_ID, 1, 20)).thenReturn(expected);

        Page<InsuranceQueryResult> actual = new InsuranceAppQueryService(queryGateway, queryService)
                .findPageByConditions("INS", "CUSTOMER_001", "PRODUCT_001", null, TENANT_ID, 1, 20);

        assertEquals(47, actual.getTotalElements());
        assertEquals(1, actual.getNumber());
        assertEquals(List.of(item), actual.getContent());
        verifyNoInteractions(queryGateway);
    }

    @Test
    void proposalPageKeepsTotalAndBypassesAxon() {
        QueryGateway queryGateway = mock(QueryGateway.class);
        ProposalQueryService queryService = mock(ProposalQueryService.class);
        ProposalQueryResult item = new ProposalQueryResult();
        Page<ProposalQueryResult> expected = new PageImpl<>(List.of(item), PageRequest.of(3, 20), 88);
        when(queryService.findProposalsPageByConditions("PRP", "CUSTOMER_001", "P001", null, TENANT_ID, 3,
                20)).thenReturn(expected);

        Page<ProposalQueryResult> actual = new ProposalAppQueryService(queryGateway, queryService)
                .findPageByConditions("PRP", "CUSTOMER_001", "P001", null, TENANT_ID, 3, 20);

        assertEquals(88, actual.getTotalElements());
        assertEquals(3, actual.getNumber());
        assertEquals(List.of(item), actual.getContent());
        verifyNoInteractions(queryGateway);
    }
}
