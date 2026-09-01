package com.titanium.policy.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.metadata.enums.billing.PremiumCollectionMode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.metadata.valueobject.Money;
import com.titanium.payment.api.PaymentApi;
import com.titanium.payment.api.request.CreatePaymentOrderRequest;
import com.titanium.payment.api.response.PaymentOrderResponse;
import com.titanium.policy.valueobject.payment.PremiumPaymentRequest;

class PaymentServiceAdapterTest {

    private static final String POLICY_ID = "POLICY_001";
    private static final String TENANT_ID = "TENANT_001";

    private PaymentApi paymentApi;
    private PaymentServiceAdapter adapter;

    @BeforeEach
    void setUp() {
        paymentApi = mock(PaymentApi.class);
        adapter = new PaymentServiceAdapter(paymentApi);
        when(paymentApi.createPaymentOrder(any(), eq(TENANT_ID)))
                .thenReturn(ApiResponse.success(paymentOrder()));
    }

    @Test
    void mapsOnlineCollectionToUnionPayChannel() {
        assertPaymentChannel(PremiumCollectionMode.ONLINE, "UNION_PAY");
    }

    @Test
    void mapsWithholdCollectionToBankChannel() {
        assertPaymentChannel(PremiumCollectionMode.WITHHOLD, "BANK");
    }

    @Test
    void mapsOfflineCollectionToBankChannel() {
        assertPaymentChannel(PremiumCollectionMode.OFFLINE, "BANK");
    }

    private void assertPaymentChannel(PremiumCollectionMode collectionMode, String expectedChannel) {
        adapter.createPaymentOrder(new PremiumPaymentRequest(POLICY_ID, "BILL_001", "CUSTOMER_001",
                Money.of(new BigDecimal("1000.00"), "CNY"), collectionMode, "保险费收取", TENANT_ID));

        ArgumentCaptor<CreatePaymentOrderRequest> requestCaptor =
                ArgumentCaptor.forClass(CreatePaymentOrderRequest.class);
        verify(paymentApi).createPaymentOrder(requestCaptor.capture(), eq(TENANT_ID));
        assertEquals(expectedChannel, requestCaptor.getValue().getPaymentMethod());
    }

    private PaymentOrderResponse paymentOrder() {
        PaymentOrderResponse response = new PaymentOrderResponse();
        response.setOrderId("PAYMENT_001");
        response.setPolicyId(POLICY_ID);
        response.setAmount(new BigDecimal("1000.00"));
        response.setCurrency("CNY");
        response.setStatus("PENDING");
        return response;
    }
}
