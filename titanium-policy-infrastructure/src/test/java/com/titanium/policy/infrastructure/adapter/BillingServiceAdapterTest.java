package com.titanium.policy.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;

import com.titanium.billing.api.BillApi;
import com.titanium.billing.api.BillingAccountApi;
import com.titanium.billing.api.request.CreateBillRequest;
import com.titanium.billing.api.request.GenerateScheduleRequest;
import com.titanium.billing.api.response.BillResponse;
import com.titanium.billing.api.response.BillingAccountResponse;
import com.titanium.common.exception.BusinessException;
import com.titanium.metadata.errorcode.BillingErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.infrastructure.adapter.mapper.BillingRequestMapper;
import com.titanium.policy.valueobject.billing.BillingResult;
import com.titanium.policy.valueobject.billing.PremiumBillRequest;
import com.titanium.policy.valueobject.billing.PremiumScheduleRequest;
import com.titanium.policy.valueobject.pricing.PremiumCalculationReference;

class BillingServiceAdapterTest {

    private static final String POLICY_ID = "POLICY_001";
    private static final String ACCOUNT_ID = "ACCOUNT_001";

    private BillApi billApi;
    private BillingAccountApi billingAccountApi;
    private BillingRequestMapper billingRequestMapper;
    private BillingServiceAdapter adapter;

    @BeforeEach
    void setUp() {
        billApi = mock(BillApi.class);
        billingAccountApi = mock(BillingAccountApi.class);
        billingRequestMapper = mock(BillingRequestMapper.class);
        adapter = new BillingServiceAdapter(billApi, billingAccountApi, billingRequestMapper);
    }

    @Test
    void preservesSynchronousBillingAccountIdFromBillResponse() {
        CreateBillRequest apiRequest = new CreateBillRequest();
        BillResponse response = new BillResponse();
        response.setBillId("BILL_001");
        response.setBillingAccountId(ACCOUNT_ID);
        when(billingRequestMapper.toCreateBillRequest(any())).thenReturn(apiRequest);
        when(billApi.createBill(apiRequest)).thenReturn(ApiResponse.success(response));

        BillingResult result = adapter.createPremiumBill(new PremiumBillRequest(POLICY_ID, "CUSTOMER_001",
                Money.of(new BigDecimal("1000.00"), "CNY"), null, "TENANT_001"));

        assertTrue(result.success());
        assertEquals("BILL_001", result.billId());
        assertEquals(ACCOUNT_ID, result.billingAccountId());
    }

    @Test
    void mapsPremiumBillDueDateToBillingRequest() {
        BillingRequestMapper mapper = Mappers.getMapper(BillingRequestMapper.class);
        LocalDate dueDate = LocalDate.of(2026, 9, 1);

        CreateBillRequest request = mapper.toCreateBillRequest(new PremiumBillRequest(POLICY_ID, "CUSTOMER_001",
                Money.of(new BigDecimal("1000.00"), "CNY"), null, dueDate, "TENANT_001"));

        assertEquals(dueDate, request.getDueDate());
    }

    @Test
    void mapsConfirmedPremiumReferencesToBillingRequest() {
        BillingRequestMapper mapper = Mappers.getMapper(BillingRequestMapper.class);
        PremiumCalculationReference reference = new PremiumCalculationReference(
                "CALC-1", "RESULT-HASH", "PRODUCT-1", "V3", "PP-7",
                new BigDecimal("1200.00"), "CNY");

        CreateBillRequest request = mapper.toCreateBillRequest(new PremiumBillRequest(
                POLICY_ID, "CUSTOMER_001", Money.of(new BigDecimal("1200.00"), "CNY"), null,
                LocalDate.of(2026, 9, 1), "TENANT_001", List.of(reference)));

        assertEquals(1, request.getPremiumCalculations().size());
        assertEquals("CALC-1", request.getPremiumCalculations().getFirst().calculationId());
        assertEquals("RESULT-HASH", request.getPremiumCalculations().getFirst().resultHash());
    }

    @Test
    void generatesPremiumScheduleAndMapsAllFields() {
        BillingAccountResponse account = account(ACCOUNT_ID);
        when(billingAccountApi.generateSchedule(any(), any())).thenReturn(ApiResponse.success(account));

        adapter.generatePremiumSchedule(request());

        ArgumentCaptor<GenerateScheduleRequest> requestCaptor = ArgumentCaptor.forClass(GenerateScheduleRequest.class);
        verify(billingAccountApi).generateSchedule(org.mockito.ArgumentMatchers.eq(ACCOUNT_ID),
                requestCaptor.capture());
        GenerateScheduleRequest actual = requestCaptor.getValue();
        assertEquals("ANNUAL", actual.getPaymentMode());
        assertEquals(10, actual.getTotalPeriods());
        assertEquals(new BigDecimal("1000.00"), actual.getInstallmentAmount());
        assertEquals("CNY", actual.getCurrency());
        assertEquals(LocalDate.of(2026, 8, 13), actual.getFirstDueDate());
        assertEquals("SYSTEM_ISSUANCE", actual.getOperatedBy());
        assertEquals("TENANT_001", actual.getTenantId());
        verify(billingAccountApi, never()).getBillingAccountByPolicyId(any(), any());
    }

    @Test
    void propagatesWhenAccountIdIsBlank() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> adapter.generatePremiumSchedule(request(" ")));

        assertTrue(exception.getMessage().contains("accountId为空"));
        verifyNoInteractions(billingAccountApi);
    }

    @Test
    void propagatesWhenScheduleResponseIsNull() {
        when(billingAccountApi.generateSchedule(any(), any())).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> adapter.generatePremiumSchedule(request()));

        assertTrue(exception.getMessage().contains("生成期缴计划失败"));
        assertTrue(exception.getMessage().contains(ACCOUNT_ID));
    }

    @Test
    void propagatesWhenScheduleResponseReportsFailure() {
        ApiResponse<BillingAccountResponse> failure = ApiResponse.error(BillingErrorCode.INVOICE_UPDATE_FAILED);
        when(billingAccountApi.generateSchedule(any(), any())).thenReturn(failure);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> adapter.generatePremiumSchedule(request()));

        assertTrue(exception.getMessage().contains(BillingErrorCode.INVOICE_UPDATE_FAILED.getMessage()));
    }

    @Test
    void propagatesWhenScheduleResponseDataIsNull() {
        when(billingAccountApi.generateSchedule(any(), any())).thenReturn(ApiResponse.success());

        assertThrows(BusinessException.class, () -> adapter.generatePremiumSchedule(request()));
    }

    @Test
    void wrapsScheduleExceptionAndPreservesCause() {
        RuntimeException cause = new RuntimeException("billing unavailable");
        when(billingAccountApi.generateSchedule(any(), any())).thenThrow(cause);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> adapter.generatePremiumSchedule(request()));

        assertInstanceOf(BusinessException.class, exception);
        assertSame(cause, exception.getCause());
        assertTrue(exception.getMessage().contains("生成期缴计划异常"));
        assertTrue(exception.getMessage().contains(ACCOUNT_ID));
    }

    private BillingAccountResponse account(String accountId) {
        BillingAccountResponse account = new BillingAccountResponse();
        account.setAccountId(accountId);
        account.setPolicyId(POLICY_ID);
        return account;
    }

    private PremiumScheduleRequest request() {
        return request(ACCOUNT_ID);
    }

    private PremiumScheduleRequest request(String accountId) {
        return new PremiumScheduleRequest(POLICY_ID, accountId, "ANNUAL", 10, new BigDecimal("1000.00"), "CNY",
                LocalDate.of(2026, 8, 13), "TENANT_001");
    }
}
