package com.titanium.policy.infrastructure.adapter.underwriting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.titanium.common.context.RequestContextHolder;
import com.titanium.common.exception.BusinessException;
import com.titanium.metadata.enums.customer.CustomerEnum.CustomerGender;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.UnderwritingStatus;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.UnderwritingType;
import com.titanium.metadata.errorcode.SystemErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.valueobject.insurance.UnderwritingDecisionRequest;
import com.titanium.policy.valueobject.insurance.UnderwritingResult;
import com.titanium.underwriting.api.UnderwritingApi;
import com.titanium.underwriting.api.request.underwriting.AutoDecideUnderwritingRequest;
import com.titanium.underwriting.api.response.underwriting.UnderwritingResponse;

/**
 * 核保决策网关同步适配器测试（g02-04 收敛后：粗粒度一次调用）
 * <p>
 * 锁死三类语义：① <b>只调一次粗粒度端点</b>（不得退回四步细粒度写）；② <b>投保单号只进幂等键字段</b>
 * （不得塞进对端 {@code policyId}）；③ G02/AC-01 四要素「未提供即不写入」。
 * </p>
 */
class SyncUnderwritingDecisionAdapterTest {

    private static final String INSURANCE_ID = "INSURANCE_001";
    private static final String HOLDER_ID = "HOLDER_001";
    private static final String UNDERWRITING_ID = "UW_001";
    private static final String PRODUCT_CODE = "PRODUCT_001";
    private static final String TENANT_ID = "TENANT_001";
    private static final BigDecimal SUM_INSURED = new BigDecimal("500000.00");

    /** 系统主体标识（与适配器常量逐字一致；断言其「绝不回落 holderId」） */
    private static final String SYSTEM_OPERATOR = "SYSTEM_AUTO_UNDERWRITING";

    /** 序列化载荷断言用（Feign 出站经 Jackson 编码，载荷形态即本对象图） */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private UnderwritingApi underwritingApi;
    private SyncUnderwritingDecisionAdapter adapter;

    @BeforeEach
    void setUp() {
        underwritingApi = mock(UnderwritingApi.class);
        adapter = new SyncUnderwritingDecisionAdapter(underwritingApi);
        // 粗粒度端点未桩时返回 null ⇒ 按失败关闭拒绝，故各用例须显式表态
        when(underwritingApi.autoDecide(any())).thenReturn(ApiResponse.success(decision()));
    }

    @AfterEach
    void tearDown() {
        // RequestContextHolder 是 ThreadLocal，用例间必须清理，否则操作人身份跨用例泄漏
        RequestContextHolder.clear();
    }

    // ---------------------------------------------------------------- 调用粒度

    @Test
    void drivesSingleCoarseGrainedCallInsteadOfStepwiseRemoteWrites() {
        adapter.requestDecision(request(new BigDecimal("1200.00")));

        // G02/AC-05：一次调用完成「创建/幂等复用 + 提交输入 + 出具决策」——
        // 适配器不再持有编排职责，任何细粒度远端写复活都会让本断言失败
        verify(underwritingApi).autoDecide(any(AutoDecideUnderwritingRequest.class));
        verify(underwritingApi, never()).getUnderwritingByPolicyId(any());
        verify(underwritingApi, never()).createUnderwriting(any());
        verify(underwritingApi, never()).submitInput(any(), any());
        verify(underwritingApi, never()).decide(any(), any());
        verifyNoMoreInteractions(underwritingApi);
    }

    @Test
    void sendsInsuranceIdAsIdempotencyKeyAndNeverWritesItIntoPolicyIdField() throws Exception {
        adapter.requestDecision(request(new BigDecimal("1200.00")));

        AutoDecideUnderwritingRequest payload = captureAutoDecideRequest();
        // 幂等键 = 投保单号：核保域据此在同事务内判定复用/续跑/新建，Saga 重试不产生第二张核保单
        assertEquals(INSURANCE_ID, payload.getInsuranceId());

        // 🔴 G02/AC-05 反向判据的行为化：投保单号**不得**再装进对端的 policyId（那是保单号语义，
        // 保全核保路径写的是真实保单号）。字段级判据比 grep 更牢——载荷里出现该键即失败
        String json = OBJECT_MAPPER.writeValueAsString(payload);
        JsonNode root = OBJECT_MAPPER.readTree(json);
        assertTrue(root.has("insuranceId"), json);
        assertFalse(root.has("policyId"), json);
    }

    // ---------------------------------------------------------------- 契约字段映射

    @Test
    void mapsCoreFieldsFromDecisionRequestOntoAutoDecidePayload() {
        adapter.requestDecision(request(new BigDecimal("1200.00")));

        AutoDecideUnderwritingRequest payload = captureAutoDecideRequest();
        // 保额取真实保额而非保费（历史缺陷：曾把保费当保额传给核保域）
        assertEquals(SUM_INSURED, payload.getAmount());
        assertEquals(HOLDER_ID, payload.getCustomerId());
        assertEquals("CNY", payload.getCurrency());
        // 险种编码取投保险种列表首个（承保主险载体）
        assertEquals(PRODUCT_CODE, payload.getProductCode());
        assertEquals(UnderwritingType.NEW_BUSINESS, payload.getUnderwritingType());
    }

    @Test
    void transmitsAllFourInsuredRiskFactorsToUnderwritingDomain() {
        // G02/AC-01：年龄/性别由承保前置链路（被保人参与方清单）提供，职业类别/BMI 在 Saga 侧
        // 无数据源（InsuredInfo 无对应字段）——此处四项一并给出，锁死「四要素通道全通」
        adapter.requestDecision(
                requestWithRiskFactors(45, CustomerGender.MALE, 4, new BigDecimal("29")));

        AutoDecideUnderwritingRequest payload = captureAutoDecideRequest();
        assertEquals(Integer.valueOf(45), payload.getAge());
        assertEquals(CustomerGender.MALE, payload.getGender());
        assertEquals(Integer.valueOf(4), payload.getOccupationCategory());
        assertEquals(new BigDecimal("29"), payload.getBmi());
    }

    @Test
    void omitsUnprovidedRiskFactorsInsteadOfWritingPlaceholderZeros() throws Exception {
        adapter.requestDecision(request(new BigDecimal("1200.00")));

        AutoDecideUnderwritingRequest payload = captureAutoDecideRequest();
        assertNull(payload.getAge());
        assertNull(payload.getGender());
        assertNull(payload.getOccupationCategory());
        assertNull(payload.getBmi());

        // 🔴 未提供 ≠ 提供了 0：载荷中不得出现「0 岁」「0 类职业」之类的占位假值——核保域据此
        // 区分「未提供」与「提供了 0」，写假值会让「无输入」被读成「低风险输入」，风险方向反转
        String json = OBJECT_MAPPER.writeValueAsString(payload);
        assertFalse(json.contains("\"age\":0"), json);
        assertFalse(json.contains("\"occupationCategory\":0"), json);
        JsonNode age = OBJECT_MAPPER.readTree(json).get("age");
        assertTrue(age == null || age.isNull(), json);
    }

    // ---------------------------------------------------------------- 审计主体

    @Test
    void reportsSystemOperatorInsteadOfFallingBackToHolder() {
        adapter.requestDecision(request(new BigDecimal("1200.00")));

        // m21-03：无人工操作人时回落「系统主体」，**绝不回落 holderId**——
        // 把投保人记为操作人会让核保记录显示「本人提交并本人决策」，审计留痕失真
        String operatorId = captureAutoDecideRequest().getOperatorId();
        assertEquals(SYSTEM_OPERATOR, operatorId);
        assertFalse(operatorId.contains(HOLDER_ID));
    }

    @Test
    void reportsRealOperatorWhenRequestContextCarriesOne() {
        RequestContextHolder.setOperatorId("OPERATOR_007");

        adapter.requestDecision(request(new BigDecimal("1200.00")));

        assertEquals("OPERATOR_007", captureAutoDecideRequest().getOperatorId());
    }

    // ---------------------------------------------------------------- 结论翻译

    @Test
    void prefersBusinessConclusionOverTransportStatus() {
        stubDecision(decision(ConclusionType.MODIFY, UnderwritingStatus.REJECTED));

        UnderwritingResult result = adapter.requestDecision(request(new BigDecimal("1200.00")));

        assertEquals(ConclusionType.MODIFY, result.resultCode());
    }

    @Test
    void fallsBackToStatusForLegacyResponseWithoutConclusionType() {
        stubDecision(decision(null, UnderwritingStatus.RATED));

        UnderwritingResult result = adapter.requestDecision(request(new BigDecimal("1200.00")));

        assertEquals(ConclusionType.MODIFY, result.resultCode());
    }

    @Test
    void propagatesUnderwritingIdAndStructuredExtraPremiumRatio() {
        UnderwritingResponse decided = decision(ConclusionType.MODIFY, UnderwritingStatus.RATED);
        decided.setExtraPremiumRatio(new BigDecimal("0.30"));
        decided.setSurchargeReason("高血压加费");
        decided.setReviewComments("次标准体承保");
        stubDecision(decided);

        UnderwritingResult result = adapter.requestDecision(request(new BigDecimal("1200.00")));

        assertEquals(UNDERWRITING_ID, result.underwritingId());
        // UW-3：结构化加费率须原样回传出单 Saga（标准保费 × (1 + ratio)），丢失即少收保费
        assertEquals(new BigDecimal("0.30"), result.extraPremiumRatio());
        assertEquals("高血压加费", result.condition());
        assertEquals("次标准体承保", result.underwritingOpinion());
    }

    // ---------------------------------------------------------------- 失败关闭

    @Test
    void rejectsNullResponse() {
        when(underwritingApi.autoDecide(any())).thenReturn(null);

        assertFailureContains("无响应", () -> adapter.requestDecision(request(new BigDecimal("1200.00"))));
    }

    @Test
    void rejectsErrorEnvelope() {
        when(underwritingApi.autoDecide(any()))
                .thenReturn(ApiResponse.error(SystemErrorCode.DEPENDENCY_SERVICE_ERROR));

        // 2xx + 错误码：契约层失败必须显式抛出，不得并入「响应体为空/结论缺失」被读成业务事实
        assertFailureContains("自动决策失败", () -> adapter.requestDecision(request(new BigDecimal("1200.00"))));
    }

    @Test
    void rejectsEmptyResponseBody() {
        when(underwritingApi.autoDecide(any())).thenReturn(ApiResponse.success());

        assertFailureContains("自动决策响应体为空",
                () -> adapter.requestDecision(request(new BigDecimal("1200.00"))));
    }

    @Test
    void rejectsBlankUnderwritingId() {
        UnderwritingResponse decided = decision(ConclusionType.ACCEPT, UnderwritingStatus.APPROVED);
        decided.setUnderwritingId(" ");
        stubDecision(decided);

        assertFailureContains("核保单ID为空", () -> adapter.requestDecision(request(new BigDecimal("1200.00"))));
    }

    @Test
    void rejectsDecisionWithoutConclusionOrLegacyStatus() {
        stubDecision(decision(null, null));

        assertFailureContains("核保结论为空", () -> adapter.requestDecision(request(new BigDecimal("1200.00"))));
    }

    // ---------------------------------------------------------------- 测试夹具

    private void stubDecision(UnderwritingResponse decided) {
        when(underwritingApi.autoDecide(any(AutoDecideUnderwritingRequest.class)))
                .thenReturn(ApiResponse.success(decided));
    }

    /** 默认决策响应：已决策、结论接受 */
    private UnderwritingResponse decision() {
        return decision(ConclusionType.ACCEPT, UnderwritingStatus.APPROVED);
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

    /** 构造携带四要素的核保决策请求（G02/AC-01） */
    private UnderwritingDecisionRequest requestWithRiskFactors(Integer age, CustomerGender gender,
                                                               Integer occupationCategory, BigDecimal bmi) {
        return new UnderwritingDecisionRequest(INSURANCE_ID, HOLDER_ID, 1, SUM_INSURED, new BigDecimal("1200.00"),
                "CNY", List.of(PRODUCT_CODE), TENANT_ID, age, gender, occupationCategory, bmi);
    }

    /** 捕获适配器实际发给核保域的一次性载荷 */
    private AutoDecideUnderwritingRequest captureAutoDecideRequest() {
        ArgumentCaptor<AutoDecideUnderwritingRequest> captor =
                ArgumentCaptor.forClass(AutoDecideUnderwritingRequest.class);
        verify(underwritingApi).autoDecide(captor.capture());
        return captor.getValue();
    }

    private void assertFailureContains(String message, Runnable invocation) {
        BusinessException exception = assertThrows(BusinessException.class, invocation::run);
        assertTrue(exception.getMessage().contains(message), exception.getMessage());
    }
}
