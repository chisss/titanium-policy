package com.titanium.policy.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.common.enums.RuleEngineDecision;
import com.titanium.ruleengine.api.RuleEngineApi;
import com.titanium.ruleengine.api.response.RuleExecutionResultResponse;
import com.titanium.ruleengine.common.enums.RuleDecision;

class RuleEngineServiceAdapterTest {

    private static final String RULE_SET_CODE = "BASIC_UNDERWRITING";
    private static final String TENANT_ID = "TENANT_001";

    private RuleEngineApi ruleEngineApi;
    private RuleEngineServiceAdapter adapter;

    @BeforeEach
    void setUp() {
        ruleEngineApi = mock(RuleEngineApi.class);
        adapter = new RuleEngineServiceAdapter(ruleEngineApi);
    }

    @Test
    void mapsPassDecisionToTrue() {
        when(ruleEngineApi.execute(RULE_SET_CODE, Map.of(), TENANT_ID))
                .thenReturn(ApiResponse.success(result(RuleDecision.PASS)));

        assertEquals(RuleEngineDecision.PASS, adapter.executeRule(RULE_SET_CODE, Map.of(), TENANT_ID));
    }

    @Test
    void mapsRejectDecisionToFalse() {
        when(ruleEngineApi.execute(RULE_SET_CODE, Map.of(), TENANT_ID))
                .thenReturn(ApiResponse.success(result(RuleDecision.REJECT)));

        assertEquals(RuleEngineDecision.REJECT, adapter.executeRule(RULE_SET_CODE, Map.of(), TENANT_ID));
    }

    @Test
    void mapsReferDecisionWithoutTreatingItAsRejection() {
        when(ruleEngineApi.execute(RULE_SET_CODE, Map.of(), TENANT_ID))
                .thenReturn(ApiResponse.success(result(RuleDecision.REFER)));

        assertEquals(RuleEngineDecision.REFER, adapter.executeRule(RULE_SET_CODE, Map.of(), TENANT_ID));
    }

    private RuleExecutionResultResponse result(RuleDecision decision) {
        return RuleExecutionResultResponse.builder().decision(decision).build();
    }
}
