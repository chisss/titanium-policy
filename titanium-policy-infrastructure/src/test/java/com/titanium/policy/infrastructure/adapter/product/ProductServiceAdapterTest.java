package com.titanium.policy.infrastructure.adapter.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import com.titanium.metadata.enums.underwriting.HealthDeclarationHandling;
import com.titanium.metadata.enums.underwriting.HealthDeclarationQuestion;
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.exception.PolicyBusinessRuleException;
import com.titanium.product.api.ProductApi;
import com.titanium.product.api.ProductTemplateApi;
import com.titanium.product.api.response.config.HealthDeclarationItemResponse;
import com.titanium.product.api.response.config.InsureConditionResponse;
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

    /**
     * 告知项清单翻译（g12-02）：问题编码经 {@code fromCode} 解析为元数据枚举、处置码解析为处置枚举；
     * 未知问题编码跳过（对端新增编码的滚动升级窗口，m8-1103 读取路径宽容判据）。
     */
    @Test
    void shouldMapHealthDeclarationItemsIntoIssueRules() {
        ProductResponse product = product(IssuanceMode.ONE_STEP);
        product.setInsureCondition(insureCondition(List.of(
                new HealthDeclarationItemResponse("HOSPITALIZATION_TWO_YEARS", "近两年住院史", true, "REJECT"),
                new HealthDeclarationItemResponse("SURGERY_HISTORY", "手术史", true, "RECORD_ONLY"),
                new HealthDeclarationItemResponse("UNKNOWN_QUESTION", "对端新增项", true, "REJECT"))));
        when(productApi.getProductById("product-1")).thenReturn(ApiResponse.success(product));
        when(productTemplateApi.getByProductId("product-1")).thenReturn(ApiResponse.success(null));

        ProductServiceAdapter adapter = new ProductServiceAdapter(OBJECT_MAPPER, productApi, productTemplateApi);

        var items = adapter.getIssueRules("product-1").healthDeclarationItems();
        assertEquals(2, items.size(), "未知问题编码应跳过而非整链失败");
        assertEquals(HealthDeclarationQuestion.HOSPITALIZATION_TWO_YEARS, items.get(0).question());
        assertEquals(HealthDeclarationHandling.REJECT, items.get(0).handling());
        assertTrue(items.get(0).required());
        assertEquals(HealthDeclarationHandling.RECORD_ONLY, items.get(1).handling());
    }

    /** 处置码未识别时承接为 null（语义：仅必答校验、不执行处置），不抛出、不丢弃该告知项本身。 */
    @Test
    void shouldTolerateUnrecognizedHandlingCode() {
        ProductResponse product = product(IssuanceMode.ONE_STEP);
        product.setInsureCondition(insureCondition(List.of(
                new HealthDeclarationItemResponse("CHRONIC_DISEASE", "慢性病史", false, "ROLLBACK_XYZ"))));
        when(productApi.getProductById("product-1")).thenReturn(ApiResponse.success(product));
        when(productTemplateApi.getByProductId("product-1")).thenReturn(ApiResponse.success(null));

        ProductServiceAdapter adapter = new ProductServiceAdapter(OBJECT_MAPPER, productApi, productTemplateApi);

        var items = adapter.getIssueRules("product-1").healthDeclarationItems();
        assertEquals(1, items.size());
        assertEquals(HealthDeclarationQuestion.CHRONIC_DISEASE, items.get(0).question());
        assertNull(items.get(0).handling(), "未识别处置按「仅必答校验」承接为 null");
    }

    /** 产品未配置告知项时清单为空——消费侧语义「空 = 不校验」，防老产品出单被误拦（AC-02 反向判据）。 */
    @Test
    void shouldKeepHealthDeclarationItemsEmptyWhenNotConfigured() {
        ProductResponse product = product(IssuanceMode.ONE_STEP);
        when(productApi.getProductById("product-1")).thenReturn(ApiResponse.success(product));
        when(productTemplateApi.getByProductId("product-1")).thenReturn(ApiResponse.success(null));

        ProductServiceAdapter adapter = new ProductServiceAdapter(OBJECT_MAPPER, productApi, productTemplateApi);

        var items = adapter.getIssueRules("product-1").healthDeclarationItems();
        assertTrue(items.isEmpty(), "未配置（条件缺失）与配置为空列表两态必须同为空清单");
    }

    /** 投保条件 Response 工厂：只填告知项清单，其余组件对出单规则校验无影响置空。 */
    private InsureConditionResponse insureCondition(List<HealthDeclarationItemResponse> items) {
        return new InsureConditionResponse(null, null, List.of(), List.of(), null, null, null, null, null,
                List.of(), List.of(), null, null, null, items);
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
