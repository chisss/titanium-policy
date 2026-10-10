package com.titanium.policy.infrastructure.messaging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;
import com.titanium.policy.application.command.insurance.InsuranceApplicationService;
import com.titanium.policy.valueobject.insurance.UnderwritingResult;

/**
 * 核保结论异步回流轨入站监听器用例（policy 域）——AC-06 (c) 两轨一致性
 * <p>
 * 核保结论有两条独立通道抵达本域：同步轨（{@code SyncUnderwritingDecisionAdapter} 经
 * {@code UnderwritingDecisionGateway} 即时拉取）与异步轨（本监听器消费 {@code underwriting-decided}）。
 * 核保域 {@code UnderwritingKafkaEventPublisher} 对每个 {@code UnderwritingDecidedEvent}
 * <b>无条件</b>发布，故<b>同一决策两轨都会到</b>，去重由投保单聚合 {@code Insurance.isRedeliveredResult}
 * 承担——而它<b>只比较核保单号 + 结论</b>两个字段。
 * </p>
 * <p>
 * 因此本类的核心断言是：<b>异步轨从报文里解析出的这两个字段，必须与同步轨装配出的逐值等价</b>。
 * 任一字段在翻译环节漂移（漏解析 / 取错嵌套值对象 / 结论枚举解析失败静默降级），聚合就会把「同一决策的重投」
 * 误判为「新决策或改判」而抛异常，Kafka 消费位点阻塞、消息永久丢失（Spring Kafka 默认错误处理器重试耗尽后跳过，
 * LAG=0、仅留 ERROR 日志）。跨轨形态用例见 domain 侧 {@code InsuranceUnderwritingResultIdempotencyTest}。
 * </p>
 * <p>
 * 次级断言锁定报文契约的三条边界：投保单号双读（新字段优先、存量回退）、缺键/缺租户不调用下游且不抛出
 * （消费不阻塞）、加费率透传。{@code runAsTenant} 为静态 ThreadLocal 工具，可脱离 Spring 直测。
 * </p>
 */
class UnderwritingDecidedEventListenerTest {

    private static final String TENANT_ID = "TENANT-001";
    private static final String INSURANCE_ID = "INS-001";
    private static final String POLICY_ID = "POL-LEGACY-001";
    private static final String UNDERWRITING_ID = "UW-001";
    private static final String DECIDED_BY = "UW-001";

    private InsuranceApplicationService insuranceApplicationService;
    private UnderwritingDecidedEventListener listener;

    @BeforeEach
    void setUp() {
        insuranceApplicationService = mock(InsuranceApplicationService.class);
        listener = new UnderwritingDecidedEventListener(insuranceApplicationService);
    }

    // ==================== (c) 两轨一致性 ====================

    @Test
    @DisplayName("异步轨解析出的核保单号与结论，与同步轨装配的逐值等价（聚合幂等判定的全部依据）")
    void shouldTranslateDecisionIntoIdenticalIdempotencyKeyAsSyncTrack() {
        listener.onUnderwritingDecided(payload(UNDERWRITING_ID, POLICY_ID, INSURANCE_ID, "ACCEPT", TENANT_ID));

        UnderwritingResult result = captureResult();
        assertEquals(UNDERWRITING_ID, result.underwritingId(),
                "核保单号是幂等键的一半：翻译漂移会让跨轨重投被误判为新决策而抛异常");
        assertEquals(ConclusionType.ACCEPT, result.resultCode(),
                "结论是幂等键的另一半：解析失败会被静默降级为 POSTPONE ⇒ 跨轨重投被误判为改判");
    }

    @Test
    @DisplayName("异步轨结论形态：opinion 与 underwriterId 同取决策人、condition 恒空、加费率透传")
    void shouldAssembleResultInAsyncTrackShape() {
        listener.onUnderwritingDecided(payload(UNDERWRITING_ID, POLICY_ID, INSURANCE_ID, "ACCEPT", TENANT_ID));

        UnderwritingResult result = captureResult();
        assertEquals(DECIDED_BY, result.underwritingOpinion(), "异步轨无核保意见字段，以决策人充当");
        assertEquals(DECIDED_BY, result.underwriterId());
        assertNull(result.condition(), "异步轨报文不承载核保条件");
        assertEquals(0, new BigDecimal("0.15").compareTo(result.extraPremiumRatio()), "加费率须透传（承保算费依赖）");
    }

    // ==================== 投保单号双读（g02-04 载荷演进） ====================

    @Test
    @DisplayName("新字段 insuranceId 与存量 policyId 同时存在时，取新字段")
    void shouldPreferNewInsuranceIdOverLegacyPolicyId() {
        listener.onUnderwritingDecided(payload(UNDERWRITING_ID, POLICY_ID, INSURANCE_ID, "ACCEPT", TENANT_ID));

        assertEquals(INSURANCE_ID, captureInsuranceId());
    }

    @Test
    @DisplayName("存量报文无 insuranceId：回退 policyId，不得静默丢弃")
    void shouldFallBackToLegacyPolicyIdForStockEvents() {
        listener.onUnderwritingDecided(payload(UNDERWRITING_ID, POLICY_ID, null, "ACCEPT", TENANT_ID));

        assertEquals(POLICY_ID, captureInsuranceId(), "存量事件无反序列化回填，只读新字段会让在途投保单全部丢结论");
    }

    // ==================== 消费不阻塞契约 ====================

    @Test
    @DisplayName("投保单号与保单号均缺失：跳过且不抛出")
    void shouldSkipWhenNeitherInsuranceNorPolicyIdPresent() {
        assertDoesNotThrow(() -> listener.onUnderwritingDecided(payload(UNDERWRITING_ID, null, null, "ACCEPT", TENANT_ID)));

        verifyNoInteractions(insuranceApplicationService);
    }

    @Test
    @DisplayName("租户缺失/空白：跳过且不抛出（runAsTenant 要求租户非空，不得传入）")
    void shouldSkipWhenTenantMissingOrBlank() {
        assertDoesNotThrow(() -> listener.onUnderwritingDecided(payload(UNDERWRITING_ID, POLICY_ID, INSURANCE_ID, "ACCEPT", null)));
        assertDoesNotThrow(() -> listener.onUnderwritingDecided(payload(UNDERWRITING_ID, POLICY_ID, INSURANCE_ID, "ACCEPT", "  ")));

        verifyNoInteractions(insuranceApplicationService);
    }

    @Test
    @DisplayName("未知结论 code：按延期处理，不抛出（异常逃逸会卡住消费位点）")
    void shouldFallBackToPostponeForUnknownConclusion() {
        assertDoesNotThrow(() -> listener.onUnderwritingDecided(
                payload(UNDERWRITING_ID, POLICY_ID, INSURANCE_ID, "NOT_A_CONCLUSION", TENANT_ID)));

        assertEquals(ConclusionType.POSTPONE, captureResult().resultCode());
    }

    // ==================== 夹具 ====================

    /** 捕获回写到应用层的投保单号（第 1 实参） */
    private String captureInsuranceId() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(insuranceApplicationService).receiveUnderwritingResult(captor.capture(), any(UnderwritingResult.class),
                eq(TENANT_ID));
        return captor.getValue();
    }

    /** 捕获回写到应用层的核保结论（第 2 实参） */
    private UnderwritingResult captureResult() {
        ArgumentCaptor<UnderwritingResult> captor = ArgumentCaptor.forClass(UnderwritingResult.class);
        verify(insuranceApplicationService).receiveUnderwritingResult(anyString(), captor.capture(), eq(TENANT_ID));
        return captor.getValue();
    }

    /**
     * 装配核保决策报文。
     * <p>
     * 与核保域 {@code UnderwritingKafkaEventPublisher} 的 {@code JSON.toJSONString(event)} 线格式一致：
     * 嵌套值对象（{@code PolicyId}/{@code InsuranceId}/{@code UnderwritingId} 均为 {@code record(String value)}）
     * 序列化为 {@code {"value":"..."}}，加费值对象序列化为 {@code {"ratio":...}}，结论枚举序列化为<b>常量名</b>。
     * </p>
     *
     * @param underwritingId 核保单号，null 表示报文不含该字段
     * @param policyId       存量保单号字段，null 表示报文不含该字段
     * @param insuranceId    新增投保单号字段，null 表示报文不含该字段（模拟存量事件）
     * @param conclusionType 结论枚举常量名
     * @param tenantId       租户，null 表示报文不含该字段
     * @return JSON 报文
     */
    private String payload(String underwritingId, String policyId, String insuranceId, String conclusionType,
            String tenantId) {
        JSONObject json = new JSONObject();
        if (underwritingId != null) {
            json.put("underwritingId", Map.of("value", underwritingId));
        }
        if (policyId != null) {
            json.put("policyId", Map.of("value", policyId));
        }
        if (insuranceId != null) {
            json.put("insuranceId", Map.of("value", insuranceId));
        }
        json.put("conclusionType", conclusionType);
        json.put("decidedBy", DECIDED_BY);
        if (tenantId != null) {
            json.put("tenantId", tenantId);
        }
        json.put("extraPremium", Map.of("ratio", new BigDecimal("0.15")));
        return JSON.toJSONString(json);
    }
}
