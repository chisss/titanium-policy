package com.titanium.policy.infrastructure.adapter.ruleengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.BusinessDomainType;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.common.enums.RuleEngineDecision;
import com.titanium.ruleengine.api.RuleEngineApi;
import com.titanium.ruleengine.api.response.execution.RuleExecutionResultResponse;
import com.titanium.ruleengine.common.enums.RuleDecision;

class RuleEngineServiceAdapterTest {

    private static final String RULE_SET_CODE = "BASIC_UNDERWRITING";
    private static final String TENANT_ID     = "TENANT_001";

    /** 关联业务单号（出单业务流水号），随请求头透传给规则引擎 */
    private static final String BUSINESS_ID   = "BIZ_001";

    /** 本适配器上报的业务域类型（端口固有属性，由 adapter 常量决定而非调用方传入） */
    private static final String BUSINESS_TYPE = BusinessDomainType.POLICY.getCode();

    private RuleEngineApi ruleEngineApi;
    private RuleEngineServiceAdapter adapter;

    @BeforeEach
    void setUp() {
        ruleEngineApi = mock(RuleEngineApi.class);
        adapter = new RuleEngineServiceAdapter(ruleEngineApi);
    }

    @Test
    void mapsPassDecisionToTrue() {
        when(ruleEngineApi.execute(RULE_SET_CODE, Map.of(), TENANT_ID, BUSINESS_ID, BUSINESS_TYPE))
                .thenReturn(ApiResponse.success(result(RuleDecision.PASS)));

        assertEquals(RuleEngineDecision.PASS, adapter.executeRule(RULE_SET_CODE, Map.of(), TENANT_ID, BUSINESS_ID));
    }

    @Test
    void mapsRejectDecisionToFalse() {
        when(ruleEngineApi.execute(RULE_SET_CODE, Map.of(), TENANT_ID, BUSINESS_ID, BUSINESS_TYPE))
                .thenReturn(ApiResponse.success(result(RuleDecision.REJECT)));

        assertEquals(RuleEngineDecision.REJECT, adapter.executeRule(RULE_SET_CODE, Map.of(), TENANT_ID, BUSINESS_ID));
    }

    @Test
    void mapsReferDecisionWithoutTreatingItAsRejection() {
        when(ruleEngineApi.execute(RULE_SET_CODE, Map.of(), TENANT_ID, BUSINESS_ID, BUSINESS_TYPE))
                .thenReturn(ApiResponse.success(result(RuleDecision.REFER)));

        assertEquals(RuleEngineDecision.REFER, adapter.executeRule(RULE_SET_CODE, Map.of(), TENANT_ID, BUSINESS_ID));
    }

    private RuleExecutionResultResponse result(RuleDecision decision) {
        return RuleExecutionResultResponse.builder().decision(decision).build();
    }
}
