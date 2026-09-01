package com.titanium.policy.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.titanium.common.exception.BusinessException;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.UnderwritingStatus;
import com.titanium.policy.valueobject.insurance.UnderwritingDecisionRequest;
import com.titanium.policy.valueobject.insurance.UnderwritingResult;
import com.titanium.underwriting.api.UnderwritingApi;
import com.titanium.underwriting.api.request.CreateUnderwritingRequest;
import com.titanium.underwriting.api.request.DecideUnderwritingApiRequest;
import com.titanium.underwriting.api.request.SubmitUnderwritingInputApiRequest;
import com.titanium.underwriting.api.response.UnderwritingResponse;

class SyncUnderwritingDecisionAdapterTest {

    private static final String INSURANCE_ID = "INSURANCE_001";
    private static final String HOLDER_ID = "HOLDER_001";
    private static final String UNDERWRITING_ID = "UW_001";
    private static final String PRODUCT_CODE = "PRODUCT_001";
    private static final String TENANT_ID = "TENANT_001";
    private static final BigDecimal SUM_INSURED = new BigDecimal("500000.00");

    private UnderwritingApi underwritingApi;
    private SyncUnderwritingDecisionAdapter adapter;

    @BeforeEach
    void setUp() {
        underwritingApi = mock(UnderwritingApi.class);
        adapter = new SyncUnderwritingDecisionAdapter(underwritingApi);
    }

    @Test
    void usesRealSumInsuredInsteadOfNonNullPremiumAndOmitsIncompleteFinancialAssessment() {
        stubSuccessfulWorkflow(decision(ConclusionType.ACCEPT, UnderwritingStatus.APPROVED));

        adapter.requestDecision(request(new BigDecimal("1200.00")));

        ArgumentCaptor<CreateUnderwritingRequest> createCaptor =
                ArgumentCaptor.forClass(CreateUnderwritingRequest.class);
        verify(underwritingApi).createUnderwriting(createCaptor.capture(), eq(TENANT_ID));
        assertEquals(SUM_INSURED, createCaptor.getValue().getAmount());

        ArgumentCaptor<SubmitUnderwritingInputApiRequest> inputCaptor =
                ArgumentCaptor.forClass(SubmitUnderwritingInputApiRequest.class);
        verify(underwritingApi).submitInput(eq(UNDERWRITING_ID), inputCaptor.capture(), eq(TENANT_ID));
        assertNull(inputCaptor.getValue().getFinancialAssessment());
    }

    @Test
    void usesRealSumInsuredWhenPremiumIsNullAndOmitsIncompleteFinancialAssessment() {
        stubSuccessfulWorkflow(decision(ConclusionType.ACCEPT, UnderwritingStatus.APPROVED));

        adapter.requestDecision(request(null));

        ArgumentCaptor<CreateUnderwritingRequest> createCaptor =
                ArgumentCaptor.forClass(CreateUnderwritingRequest.class);
        verify(underwritingApi).createUnderwriting(createCaptor.capture(), eq(TENANT_ID));
        assertEquals(SUM_INSURED, createCaptor.getValue().getAmount());

        ArgumentCaptor<SubmitUnderwritingInputApiRequest> inputCaptor =
                ArgumentCaptor.forClass(SubmitUnderwritingInputApiRequest.class);
        verify(underwritingApi).submitInput(eq(UNDERWRITING_ID), inputCaptor.capture(), eq(TENANT_ID));
        assertNull(inputCaptor.getValue().getFinancialAssessment());
    }

    @Test
    void prefersBusinessConclusionOverTransportStatus() {
        stubSuccessfulWorkflow(decision(ConclusionType.MODIFY, UnderwritingStatus.REJECTED));

        UnderwritingResult result = adapter.requestDecision(request(new BigDecimal("1200.00")));

        assertEquals(ConclusionType.MODIFY, result.resultCode());
    }

    @Test
    void fallsBackToStatusForLegacyResponseWithoutConclusionType() {
        stubSuccessfulWorkflow(decision(null, UnderwritingStatus.RATED));

        UnderwritingResult result = adapter.requestDecision(request(new BigDecimal("1200.00")));

        assertEquals(ConclusionType.MODIFY, result.resultCode());
    }

    @Test
    void rejectsNonSuccessfulCreateResponse() {
        when(underwritingApi.createUnderwriting(any(), eq(TENANT_ID)))
                .thenReturn(ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new UnderwritingResponse()));

        assertFailureContains("创建核保", () -> adapter.requestDecision(request(new BigDecimal("1200.00"))));
    }

    @Test
    void rejectsEmptyCreateResponseBody() {
        ResponseEntity<UnderwritingResponse> emptyResponse = ResponseEntity.ok().build();
        when(underwritingApi.createUnderwriting(any(), eq(TENANT_ID))).thenReturn(emptyResponse);

        assertFailureContains("创建核保响应体为空",
                () -> adapter.requestDecision(request(new BigDecimal("1200.00"))));
    }

    @Test
    void rejectsBlankCreatedUnderwritingId() {
        UnderwritingResponse created = new UnderwritingResponse();
        created.setUnderwritingId(" ");
        when(underwritingApi.createUnderwriting(any(), eq(TENANT_ID))).thenReturn(ResponseEntity.ok(created));

        assertFailureContains("核保单ID为空", () -> adapter.requestDecision(request(new BigDecimal("1200.00"))));
    }

    @Test
    void rejectsNonSuccessfulSubmitInputResponse() {
        stubCreatedResponse();
        when(underwritingApi.submitInput(eq(UNDERWRITING_ID), any(), eq(TENANT_ID)))
                .thenReturn(ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new UnderwritingResponse()));

        assertFailureContains("提交核保输入", () -> adapter.requestDecision(request(new BigDecimal("1200.00"))));
    }

    @Test
    void rejectsNonSuccessfulDecisionResponse() {
        stubCreatedAndSubmittedResponses();
        when(underwritingApi.decide(eq(UNDERWRITING_ID), any(), eq(TENANT_ID)))
                .thenReturn(ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new UnderwritingResponse()));

        assertFailureContains("触发核保决策", () -> adapter.requestDecision(request(new BigDecimal("1200.00"))));
    }

    @Test
    void rejectsEmptyDecisionResponseBody() {
        stubCreatedAndSubmittedResponses();
        ResponseEntity<UnderwritingResponse> emptyResponse = ResponseEntity.ok().build();
        when(underwritingApi.decide(eq(UNDERWRITING_ID), any(), eq(TENANT_ID))).thenReturn(emptyResponse);

        assertFailureContains("核保决策响应体为空",
                () -> adapter.requestDecision(request(new BigDecimal("1200.00"))));
    }

    @Test
    void rejectsBlankDecisionUnderwritingId() {
        UnderwritingResponse decided = decision(ConclusionType.ACCEPT, UnderwritingStatus.APPROVED);
        decided.setUnderwritingId(null);
        stubSuccessfulWorkflow(decided);

        assertFailureContains("核保单ID为空", () -> adapter.requestDecision(request(new BigDecimal("1200.00"))));
    }

    @Test
    void rejectsDecisionWithoutConclusionOrLegacyStatus() {
        stubSuccessfulWorkflow(decision(null, null));

        assertFailureContains("核保结论为空", () -> adapter.requestDecision(request(new BigDecimal("1200.00"))));
    }

    private void stubSuccessfulWorkflow(UnderwritingResponse decided) {
        stubCreatedAndSubmittedResponses();
        when(underwritingApi.decide(eq(UNDERWRITING_ID), any(DecideUnderwritingApiRequest.class), eq(TENANT_ID)))
                .thenReturn(ResponseEntity.ok(decided));
    }

    private void stubCreatedAndSubmittedResponses() {
        stubCreatedResponse();
        when(underwritingApi.submitInput(eq(UNDERWRITING_ID), any(SubmitUnderwritingInputApiRequest.class),
                eq(TENANT_ID))).thenReturn(ResponseEntity.ok(new UnderwritingResponse()));
    }

    private void stubCreatedResponse() {
        UnderwritingResponse created = new UnderwritingResponse();
        created.setUnderwritingId(UNDERWRITING_ID);
        when(underwritingApi.createUnderwriting(any(CreateUnderwritingRequest.class), eq(TENANT_ID)))
                .thenReturn(ResponseEntity.ok(created));
    }

    private UnderwritingResponse decision(ConclusionType conclusionType, UnderwritingStatus status) {
        UnderwritingResponse response = new UnderwritingResponse();
        response.setUnderwritingId(UNDERWRITING_ID);
        response.setConclusionType(conclusionType);
        response.setStatus(status);
        return response;
    }

    private UnderwritingDecisionRequest request(BigDecimal premium) {
        return new UnderwritingDecisionRequest(INSURANCE_ID, HOLDER_ID, 1, SUM_INSURED, premium, "CNY",
                List.of(PRODUCT_CODE), TENANT_ID, null, null, null, null);
    }

    private void assertFailureContains(String message, Runnable invocation) {
        BusinessException exception = assertThrows(BusinessException.class, invocation::run);
        assertTrue(exception.getMessage().contains(message), exception.getMessage());
    }
}
