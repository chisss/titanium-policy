package com.titanium.policy.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.common.exception.BusinessException;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.valueobject.pricing.ConfirmedPremiumRequest;
import com.titanium.policy.valueobject.pricing.PremiumAdjustmentInput;
import com.titanium.product.api.ProductPremiumCalculationApi;
import com.titanium.product.api.request.premium.PremiumCalculationRequest;
import com.titanium.product.api.response.premium.PremiumCalculationResponse;

class ConfirmedPremiumPricingAdapterTest {

    private ProductPremiumCalculationApi api;
    private ConfirmedPremiumPricingAdapter adapter;

    @BeforeEach
    void setUp() {
        api = mock(ProductPremiumCalculationApi.class);
        adapter = new ConfirmedPremiumPricingAdapter(api);
    }

    @Test
    void translatesPolicyConfirmationToProductContract() {
        when(api.confirm(eq("PRODUCT-1"), any(), eq("TENANT-1")))
                .thenReturn(ApiResponse.success(response()));

        var result = adapter.confirm(request());

        assertEquals("CALC-1", result.calculationId());
        assertEquals(new BigDecimal("1200.00"), result.totalPremium());
        ArgumentCaptor<PremiumCalculationRequest> captor = ArgumentCaptor.forClass(PremiumCalculationRequest.class);
        verify(api).confirm(eq("PRODUCT-1"), captor.capture(), eq("TENANT-1"));
        assertEquals("ISSUANCE_CONFIRM", captor.getValue().purpose());
        assertEquals("V3", captor.getValue().productVersion());
        assertEquals("CHANNEL-1", captor.getValue().channelId());
        assertEquals(1, captor.getValue().policyYear());
        assertEquals("SURCHARGE_RATE", captor.getValue().underwritingAdjustments().getFirst().type());
    }

    @Test
    void failsClosedWhenProductDoesNotReturnCalculation() {
        when(api.confirm(eq("PRODUCT-1"), any(), eq("TENANT-1"))).thenReturn(ApiResponse.success(null));

        assertThrows(BusinessException.class, () -> adapter.confirm(request()));
    }

    private ConfirmedPremiumRequest request() {
        return new ConfirmedPremiumRequest(
                "REQ-1", "BIZ-1", "PRODUCT-1", "V3", LocalDateTime.of(2026, 9, 1, 0, 0),
                "CNY", new BigDecimal("100000"), 35, "M", 20, 20, 20,
                new com.titanium.policy.valueobject.pricing.ConfirmationContextSnapshot(
                        "LINE-1", 1, "PRODUCT-CODE-1", "INSURANCE-1", "CHANNEL-1", 1),
                List.of(new PremiumAdjustmentInput(
                        "UW-1", "SURCHARGE_RATE", new BigDecimal("0.20"), "核保加费", "UW-V1")),
                "TENANT-1");
    }

    private PremiumCalculationResponse response() {
        return new PremiumCalculationResponse(
                "CALC-1", "REQ-1", "BIZ-1", "ISSUANCE_CONFIRM", "CONFIRMED", "PRODUCT-1", "V3",
                "CNY", new BigDecimal("1000.00"), new BigDecimal("1200.00"), new BigDecimal("60.00"),
                20, List.of(), "PP-7", "PLAN-HASH", "LIFE", "V1", "TABLE-HASH", "FEATURE-1",
                "FORMULA", "V1", "ARTIFACT-HASH", "REQUEST-HASH", "INPUT-HASH", "RESULT-HASH",
                LocalDateTime.of(2026, 8, 18, 12, 0));
    }
}
