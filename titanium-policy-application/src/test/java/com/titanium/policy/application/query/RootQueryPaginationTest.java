package com.titanium.policy.application.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
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
                .findPageByConditions("POL", null, null, null, null, null, null, null, null, TENANT_ID, 2, 10);

        assertEquals(43, actual.getTotalElements());
        assertEquals(2, actual.getNumber());
        assertEquals(List.of(item), actual.getContent());
        verifyNoInteractions(queryGateway);
    }

    /**
     * 🔴 四个日期区间必须是**原值透传**，不得回退为硬编码 {@code null}。
     *
     * <p>该门面此前把四参写死为 {@code null}，症状为「加了日期条件、条数不变、无任何报错」。
     * 本用例的断言方式很关键：**不能只看返回值** —— 若实现传 null，桩未命中时 Mockito 默认返回
     * null，用例确实会 NPE 失败，但失败信息指向「返回值是 null」而非「参数被写死」，
     * 且一旦桩写得宽松（如 any()）就会恒绿。故用 {@code verify} 逐参精确锁定读侧真实收到的四个值。</p>
     */
    @Test
    void policyDateRangesArePassedThroughToReadSide() {
        QueryGateway queryGateway = mock(QueryGateway.class);
        PolicyQueryService queryService = mock(PolicyQueryService.class);
        LocalDateTime effectiveStart = LocalDateTime.of(2026, 9, 1, 0, 0, 0);
        LocalDateTime effectiveEnd = LocalDateTime.of(2026, 9, 30, 23, 59, 59);
        LocalDateTime expiryStart = LocalDateTime.of(2027, 1, 1, 0, 0, 0);
        LocalDateTime expiryEnd = LocalDateTime.of(2027, 12, 31, 23, 59, 59);
        PolicyQueryResult item = new PolicyQueryResult();
        when(queryService.findPoliciesPageByMultipleConditions("POL", null, null, null, null,
                effectiveStart, effectiveEnd, expiryStart, expiryEnd, TENANT_ID, 0, 10))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 10), 1));

        Page<PolicyQueryResult> actual = new PolicyAppQueryService(queryGateway, queryService)
                .findPageByConditions("POL", null, null, null, null,
                        effectiveStart, effectiveEnd, expiryStart, expiryEnd, TENANT_ID, 0, 10);

        // 桩命中即证明参数逐一对上（未命中则 Mockito 返回 null，下面先 NPE）。
        // ⚠️ 此处只断言 content 不断言 total：PageImpl 的构造器会在 offset+pageSize > total 时
        // 把 total 校正为 offset+content.size()，写死 total 会让用例与框架内部规则纠缠。
        // 「总数不被门面吞掉」由本类既有的 policyPageKeepsTotalAndBypassesAxon 覆盖。
        assertEquals(List.of(item), actual.getContent());
        verify(queryService).findPoliciesPageByMultipleConditions("POL", null, null, null, null,
                effectiveStart, effectiveEnd, expiryStart, expiryEnd, TENANT_ID, 0, 10);
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
