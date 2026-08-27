package com.titanium.policy.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;

import com.titanium.policy.application.command.InsuranceApplicationService;
import com.titanium.policy.application.command.PolicyApplicationService;
import com.titanium.policy.application.command.ProposalApplicationService;
import com.titanium.policy.application.query.InsuranceAppQueryService;
import com.titanium.policy.application.query.PolicyAppQueryService;
import com.titanium.policy.application.query.ProposalAppQueryService;
import com.titanium.policy.query.result.InsuranceQueryResult;
import com.titanium.policy.query.result.PolicyQueryResult;
import com.titanium.policy.query.result.ProposalQueryResult;
import com.titanium.policy.web.mapper.InsuranceWebMapper;
import com.titanium.policy.web.mapper.PolicyStatisticsWebMapper;
import com.titanium.policy.web.mapper.PolicyWebMapper;
import com.titanium.policy.web.mapper.ProposalWebMapper;
import com.titanium.policy.web.response.InsuranceVO;
import com.titanium.policy.web.response.PolicyDetailVO;
import com.titanium.policy.web.response.ProposalVO;

class RootPaginationControllerTest {

    private static final String TENANT_ID = "TENANT_001";

    @Test
    void policyPageMappingKeepsTotalElements() {
        PolicyAppQueryService queryService = mock(PolicyAppQueryService.class);
        PolicyWebMapper mapper = mock(PolicyWebMapper.class);
        PolicyQueryResult result = new PolicyQueryResult();
        PolicyDetailVO vo = new PolicyDetailVO();
        when(queryService.findPageByConditions(null, null, null, null, null, TENANT_ID, 1, 10))
                .thenReturn(new PageImpl<>(List.of(result), PageRequest.of(1, 10), 31));
        when(mapper.toVO(result)).thenReturn(vo);
        PolicyController controller = new PolicyController(mock(PolicyApplicationService.class), queryService,
                mapper, mock(PolicyStatisticsWebMapper.class));

        ResponseEntity<Page<PolicyDetailVO>> response = controller.pagePolicies(null, null, null, null, null, 1, 10,
                TENANT_ID);

        assertEquals(31, response.getBody().getTotalElements());
        assertSame(vo, response.getBody().getContent().getFirst());
    }

    @Test
    void insurancePageMappingKeepsTotalElements() {
        InsuranceAppQueryService queryService = mock(InsuranceAppQueryService.class);
        InsuranceWebMapper mapper = mock(InsuranceWebMapper.class);
        InsuranceQueryResult result = new InsuranceQueryResult();
        InsuranceVO vo = new InsuranceVO();
        when(queryService.findPageByConditions(null, null, null, null, TENANT_ID, 2, 20))
                .thenReturn(new PageImpl<>(List.of(result), PageRequest.of(2, 20), 69));
        when(mapper.toVO(result)).thenReturn(vo);
        InsuranceController controller = new InsuranceController(mock(InsuranceApplicationService.class),
                queryService, mapper);

        ResponseEntity<Page<InsuranceVO>> response = controller.pageInsurances(null, null, null, null, 2, 20,
                TENANT_ID);

        assertEquals(69, response.getBody().getTotalElements());
        assertSame(vo, response.getBody().getContent().getFirst());
    }

    @Test
    void proposalPageMappingKeepsTotalElements() {
        ProposalAppQueryService queryService = mock(ProposalAppQueryService.class);
        ProposalWebMapper mapper = mock(ProposalWebMapper.class);
        ProposalQueryResult result = new ProposalQueryResult();
        ProposalVO vo = new ProposalVO();
        when(queryService.findPageByConditions(null, null, null, null, TENANT_ID, 3, 20))
                .thenReturn(new PageImpl<>(List.of(result), PageRequest.of(3, 20), 92));
        when(mapper.toVO(result)).thenReturn(vo);
        ProposalController controller = new ProposalController(mock(ProposalApplicationService.class), queryService,
                mapper);

        ResponseEntity<Page<ProposalVO>> response = controller.pageProposals(null, null, null, null, 3, 20,
                TENANT_ID);

        assertEquals(92, response.getBody().getTotalElements());
        assertSame(vo, response.getBody().getContent().getFirst());
    }
}
