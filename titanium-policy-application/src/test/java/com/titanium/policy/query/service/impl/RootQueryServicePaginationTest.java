package com.titanium.policy.query.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.titanium.policy.query.repository.InsuranceViewRepository;
import com.titanium.policy.query.repository.PolicyViewRepository;
import com.titanium.policy.query.repository.ProposalViewRepository;
import com.titanium.policy.query.result.InsuranceQueryResult;
import com.titanium.policy.query.result.PolicyQueryResult;
import com.titanium.policy.query.result.ProposalQueryResult;
import com.titanium.policy.query.view.InsuranceView;
import com.titanium.policy.query.view.PolicyView;
import com.titanium.policy.query.view.ProposalView;

class RootQueryServicePaginationTest {

    private static final String TENANT_ID = "TENANT_001";

    @Test
    void policyMappingKeepsRepositoryTotal() {
        PolicyViewRepository repository = mock(PolicyViewRepository.class);
        PolicyView view = new PolicyView();
        view.setPolicyId("POLICY_001");
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(view), PageRequest.of(1, 10), 35));

        Page<PolicyQueryResult> page = new PolicyQueryServiceImpl(repository, null, null, null, null)
                .findPoliciesPageByMultipleConditions(null, null, null, null, null, null, null, null, null,
                        TENANT_ID, 1, 10);

        assertEquals(35, page.getTotalElements());
        assertEquals("POLICY_001", page.getContent().getFirst().getPolicyId());
    }

    @Test
    void insuranceMappingKeepsRepositoryTotal() {
        InsuranceViewRepository repository = mock(InsuranceViewRepository.class);
        InsuranceView view = new InsuranceView();
        view.setInsuranceId("INSURANCE_001");
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(view), PageRequest.of(2, 20), 65));

        Page<InsuranceQueryResult> page = new InsuranceQueryServiceImpl(repository)
                .findInsurancesPageByConditions(null, null, null, null, TENANT_ID, 2, 20);

        assertEquals(65, page.getTotalElements());
        assertEquals("INSURANCE_001", page.getContent().getFirst().getInsuranceId());
    }

    @Test
    void proposalMappingKeepsRepositoryTotal() {
        ProposalViewRepository repository = mock(ProposalViewRepository.class);
        ProposalView view = new ProposalView();
        view.setProposalId("PROPOSAL_001");
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(view), PageRequest.of(3, 20), 85));

        Page<ProposalQueryResult> page = new ProposalQueryServiceImpl(repository)
                .findProposalsPageByConditions(null, null, null, null, TENANT_ID, 3, 20);

        assertEquals(85, page.getTotalElements());
        assertEquals("PROPOSAL_001", page.getContent().getFirst().getProposalId());
    }
}
