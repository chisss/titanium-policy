package com.titanium.policy.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;

import com.titanium.policy.application.command.insurance.InsuranceApplicationService;
import com.titanium.policy.application.command.policy.PolicyApplicationService;
import com.titanium.policy.application.command.proposal.ProposalApplicationService;
import com.titanium.policy.application.query.InsuranceAppQueryService;
import com.titanium.policy.application.query.PolicyAppQueryService;
import com.titanium.policy.application.query.ProposalAppQueryService;
import com.titanium.policy.query.result.InsuranceQueryResult;
import com.titanium.policy.query.result.PolicyQueryResult;
import com.titanium.policy.query.result.ProposalQueryResult;
import com.titanium.policy.web.controller.insurance.InsuranceController;
import com.titanium.policy.web.controller.policy.PolicyController;
import com.titanium.policy.web.controller.proposal.ProposalController;
import com.titanium.policy.web.mapper.InsuranceWebMapper;
import com.titanium.policy.web.mapper.PolicyStatisticsWebMapper;
import com.titanium.policy.web.mapper.PolicyWebMapper;
import com.titanium.policy.web.mapper.ProposalWebMapper;
import com.titanium.policy.web.response.insurance.InsuranceVO;
import com.titanium.policy.web.response.policy.PolicyDetailVO;
import com.titanium.policy.web.response.proposal.ProposalVO;

class RootPaginationControllerTest {

    private static final String TENANT_ID = "TENANT_001";

    @Test
    void policyPageMappingKeepsTotalElements() {
        PolicyAppQueryService queryService = mock(PolicyAppQueryService.class);
        PolicyWebMapper mapper = mock(PolicyWebMapper.class);
        PolicyQueryResult result = new PolicyQueryResult();
        PolicyDetailVO vo = new PolicyDetailVO();
        when(queryService.findPageByConditions(null, null, null, null, null, null, null, null, null, TENANT_ID, 1,
                10)).thenReturn(new PageImpl<>(List.of(result), PageRequest.of(1, 10), 31));
        when(mapper.toVO(result)).thenReturn(vo);
        PolicyController controller = new PolicyController(mock(PolicyApplicationService.class), queryService,
                mapper, mock(PolicyStatisticsWebMapper.class));

        ResponseEntity<Page<PolicyDetailVO>> response = controller.pagePolicies(null, null, null, null, null, null,
                null, null, null, 1, 10, TENANT_ID);

        assertEquals(31, response.getBody().getTotalElements());
        assertSame(vo, response.getBody().getContent().getFirst());
    }

    /**
     * 🔴 控制器四个日期区间必须透传到应用门面 —— 与门面层「不得写死 null」是**同族缺陷的两个位点**
     * （控制器加参数却忘了传下去，同样表现为「加了日期条件、条数不变、无任何报错」）。
     * 断言用 {@code verify} 逐参锁定，避免桩写宽松后恒绿。
     */
    @Test
    void policyDateRangesArePassedThroughToQueryFacade() {
        PolicyAppQueryService queryService = mock(PolicyAppQueryService.class);
        PolicyWebMapper mapper = mock(PolicyWebMapper.class);
        LocalDateTime effectiveStart = LocalDateTime.of(2026, 9, 1, 0, 0, 0);
        LocalDateTime effectiveEnd = LocalDateTime.of(2026, 9, 30, 23, 59, 59);
        LocalDateTime expiryStart = LocalDateTime.of(2027, 1, 1, 0, 0, 0);
        LocalDateTime expiryEnd = LocalDateTime.of(2027, 12, 31, 23, 59, 59);
        when(queryService.findPageByConditions("POL", null, null, null, null, effectiveStart, effectiveEnd,
                expiryStart, expiryEnd, TENANT_ID, 0, 10))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        PolicyController controller = new PolicyController(mock(PolicyApplicationService.class), queryService,
                mapper, mock(PolicyStatisticsWebMapper.class));

        controller.pagePolicies("POL", null, null, null, null, effectiveStart, effectiveEnd, expiryStart, expiryEnd,
                0, 10, TENANT_ID);

        verify(queryService).findPageByConditions("POL", null, null, null, null, effectiveStart, effectiveEnd,
                expiryStart, expiryEnd, TENANT_ID, 0, 10);
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
