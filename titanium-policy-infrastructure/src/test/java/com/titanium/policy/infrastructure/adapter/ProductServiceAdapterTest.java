package com.titanium.policy.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.product.ProductEnum.IssuanceMode;
import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.insurance.SubjectType;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.product.api.ProductApi;
import com.titanium.product.api.ProductTemplateApi;
import com.titanium.product.api.response.IssuanceProcessConfigResponse;
import com.titanium.product.api.response.ProductResponse;
import com.titanium.product.api.response.ProductTemplateResponse;

class ProductServiceAdapterTest {

    private final ProductApi productApi = mock(ProductApi.class);
    private final ProductTemplateApi productTemplateApi = mock(ProductTemplateApi.class);

    @Test
    void shouldPreferProductIssuanceMode() {
        ProductResponse product = product(IssuanceMode.ONE_STEP);
        when(productApi.getProductById("product-1", "tenant-1")).thenReturn(ApiResponse.success(product));
        ProductServiceAdapter adapter = new ProductServiceAdapter(productApi, productTemplateApi);

        assertEquals(IssuanceMode.ONE_STEP, adapter.getIssuanceMode("product-1", "tenant-1"));
        verify(productTemplateApi, never()).getByProductId("product-1", "tenant-1");
    }

    @Test
    void shouldFallbackToBoundTemplateIssuanceMode() {
        when(productApi.getProductById("product-1", "tenant-1"))
                .thenReturn(ApiResponse.success(product(null)));
        ProductTemplateResponse template = new ProductTemplateResponse();
        template.setIssuanceMode(IssuanceMode.THREE_STEP);
        when(productTemplateApi.getByProductId("product-1", "tenant-1"))
                .thenReturn(ApiResponse.success(template));
        ProductServiceAdapter adapter = new ProductServiceAdapter(productApi, productTemplateApi);

        assertEquals(IssuanceMode.THREE_STEP, adapter.getIssuanceMode("product-1", "tenant-1"));
    }

    @Test
    void shouldExposeVehicleSubjectSchemaAsIssuanceRule() {
        ProductResponse product = product(IssuanceMode.ONE_STEP);
        product.setInsuranceType(InsuranceProductType.AUTO);
        when(productApi.getProductById("product-1", "tenant-1")).thenReturn(ApiResponse.success(product));
        ProductTemplateResponse template = new ProductTemplateResponse();
        template.setPolicyStructure(ProductTemplateResponse.PolicyStructureConfigDTO.builder()
                .subjectType(SubjectType.VEHICLE)
                .subjectFieldsSchema("{\"required\":[\"licensePlate\",\"vin\"]}")
                .build());
        when(productTemplateApi.getByProductId("product-1", "tenant-1"))
                .thenReturn(ApiResponse.success(template));

        ProductServiceAdapter adapter = new ProductServiceAdapter(productApi, productTemplateApi);

        var rules = adapter.getIssueRules("product-1", "tenant-1");
        assertEquals(SubjectType.VEHICLE, rules.subjectType());
        assertEquals(List.of("licensePlate", "vin"), rules.requiredSubjectAttributes());
    }

    private ProductResponse product(IssuanceMode mode) {
        ProductResponse product = new ProductResponse();
        product.setProductId("product-1");
        if (mode != null) {
            product.setIssuanceProcessConfig(new IssuanceProcessConfigResponse(
                    mode, List.of(), false, false, false, null, List.of()));
        }
        return product;
    }
}
