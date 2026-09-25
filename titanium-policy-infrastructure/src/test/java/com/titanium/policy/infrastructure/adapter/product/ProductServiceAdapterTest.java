package com.titanium.policy.infrastructure.adapter.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.insurance.SubjectType;
import com.titanium.metadata.enums.product.ProductEnum.IssuanceMode;
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.exception.PolicyBusinessRuleException;
import com.titanium.product.api.ProductApi;
import com.titanium.product.api.ProductTemplateApi;
import com.titanium.product.api.response.config.IssuanceProcessConfigResponse;
import com.titanium.product.api.response.product.ProductResponse;
import com.titanium.product.api.response.product.ProductTemplateResponse;

class ProductServiceAdapterTest {

    /** 被测适配器要求注入 JSON 解析器（m22-03：原为字段内裸 new）；测试用带 JavaTimeModule 的实例 */
    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().registerModule(new JavaTimeModule());

    private final ProductApi productApi = mock(ProductApi.class);
    private final ProductTemplateApi productTemplateApi = mock(ProductTemplateApi.class);

    @Test
    void shouldPreferProductIssuanceMode() {
        ProductResponse product = product(IssuanceMode.ONE_STEP);
        when(productApi.getProductById("product-1")).thenReturn(ApiResponse.success(product));
        ProductServiceAdapter adapter = new ProductServiceAdapter(OBJECT_MAPPER, productApi, productTemplateApi);

        assertEquals(IssuanceMode.ONE_STEP, adapter.getIssuanceMode("product-1"));
        verify(productTemplateApi, never()).getByProductId("product-1");
    }

    @Test
    void shouldFallbackToBoundTemplateIssuanceMode() {
        when(productApi.getProductById("product-1"))
                .thenReturn(ApiResponse.success(product(null)));
        ProductTemplateResponse template = new ProductTemplateResponse();
        template.setIssuanceMode(IssuanceMode.THREE_STEP);
        when(productTemplateApi.getByProductId("product-1"))
                .thenReturn(ApiResponse.success(template));
        ProductServiceAdapter adapter = new ProductServiceAdapter(OBJECT_MAPPER, productApi, productTemplateApi);

        assertEquals(IssuanceMode.THREE_STEP, adapter.getIssuanceMode("product-1"));
    }

    /**
     * 🔴 业务条件，不是编程错误（审查报告 EXC-05 复核）：产品与其模板均未配出单流程时，
     * 必须抛出<b>携带错误码</b>的业务异常，而非裸 {@code IllegalStateException}。
     * <p>
     * 裸 ISE 会直穿到 web 兜底成为 500 + SYSTEM_ERROR，把「该产品没配出单流程」这个
     * 调用方可自行修正的事实（换一个已配流程的产品）报成「服务端出错了」。
     * </p>
     * <p>
     * 断言取错误码而非异常类型：异常类型只证明「换了个类抛」，错误码才证明
     * <b>调用方拿到的可处置依据</b>（{@code 20007027} 经边界反查还原为
     * {@code PARAMETER_INVALID} → HTTP 400）。
     * </p>
     */
    @Test
    void shouldRejectWithErrorCodeWhenIssuanceModeNotConfigured() {
        when(productApi.getProductById("product-1")).thenReturn(ApiResponse.success(product(null)));
        when(productTemplateApi.getByProductId("product-1")).thenReturn(ApiResponse.success(null));
        ProductServiceAdapter adapter = new ProductServiceAdapter(OBJECT_MAPPER, productApi, productTemplateApi);

        PolicyBusinessRuleException exception = assertThrows(PolicyBusinessRuleException.class,
                () -> adapter.getIssuanceMode("product-1"));

        assertEquals(PolicyErrorCode.ISSUANCE_MODE_NOT_CONFIGURED.getCode(), exception.getErrorCode().getCode());
    }

    @Test
    void shouldExposeVehicleSubjectSchemaAsIssuanceRule() {
        ProductResponse product = product(IssuanceMode.ONE_STEP);
        product.setInsuranceType(InsuranceProductType.AUTO);
        when(productApi.getProductById("product-1")).thenReturn(ApiResponse.success(product));
        ProductTemplateResponse template = new ProductTemplateResponse();
        template.setPolicyStructure(ProductTemplateResponse.PolicyStructureConfigDTO.builder()
                .subjectType(SubjectType.VEHICLE)
                .subjectFieldsSchema("{\"required\":[\"licensePlate\",\"vin\"]}")
                .build());
        when(productTemplateApi.getByProductId("product-1"))
                .thenReturn(ApiResponse.success(template));

        ProductServiceAdapter adapter = new ProductServiceAdapter(OBJECT_MAPPER, productApi, productTemplateApi);

        var rules = adapter.getIssueRules("product-1");
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
