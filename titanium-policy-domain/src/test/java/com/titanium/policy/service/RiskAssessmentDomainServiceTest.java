package com.titanium.policy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.policy.common.enums.RiskAssessmentStep;
import com.titanium.policy.service.impl.RiskAssessmentDomainServiceImpl;
import com.titanium.policy.valueobject.RiskAssessmentDecision;

/**
 * 风控裁决领域服务单元测试
 * <p>
 * 领域服务无 Port/无容器依赖，直接 {@code new} 出来用纯 JUnit 测试，验证风控裁决纯规则。
 * </p>
 */
class RiskAssessmentDomainServiceTest {

    private RiskAssessmentDomainService domainService;

    @BeforeEach
    void setUp() {
        domainService = new RiskAssessmentDomainServiceImpl();
    }

    @Test
    @DisplayName("仅基础自动核保依赖规则引擎，其余步骤为本地规则")
    void shouldIdentifyRuleEngineDependentSteps() {
        assertTrue(domainService.requiresRuleEngine(RiskAssessmentStep.BASIC_UNDERWRITING));
        assertFalse(domainService.requiresRuleEngine(RiskAssessmentStep.BLACKLIST_CHECK));
        assertFalse(domainService.requiresRuleEngine(RiskAssessmentStep.DUPLICATE_CHECK));
        assertFalse(domainService.requiresRuleEngine(RiskAssessmentStep.MANUAL_UNDERWRITING));
        assertFalse(domainService.requiresRuleEngine(RiskAssessmentStep.AML_CHECK));
        assertFalse(domainService.requiresRuleEngine(RiskAssessmentStep.HEALTH_DECLARATION_CHECK));
    }

    @Test
    @DisplayName("依赖规则引擎的步骤：裁决通过与否随规则引擎结果")
    void shouldJudgeByRuleEngineOutcome() {
        RiskAssessmentDecision passed = domainService.judge(RiskAssessmentStep.BASIC_UNDERWRITING, true);
        assertTrue(passed.passed());
        assertEquals(RiskAssessmentStep.BASIC_UNDERWRITING, passed.step());

        RiskAssessmentDecision rejected = domainService.judge(RiskAssessmentStep.BASIC_UNDERWRITING, false);
        assertFalse(rejected.passed());
        assertTrue(rejected.reason().contains("规则引擎"));
    }

    @Test
    @DisplayName("本地风控步骤：人工核保放行以推进流程，其余本地步骤当前默认通过")
    void shouldPassLocalSteps() {
        assertTrue(domainService.judgeLocal(RiskAssessmentStep.MANUAL_UNDERWRITING).passed());
        assertTrue(domainService.judgeLocal(RiskAssessmentStep.BLACKLIST_CHECK).passed());
        assertTrue(domainService.judgeLocal(RiskAssessmentStep.DUPLICATE_CHECK).passed());
        assertTrue(domainService.judgeLocal(RiskAssessmentStep.AML_CHECK).passed());
        assertTrue(domainService.judgeLocal(RiskAssessmentStep.HEALTH_DECLARATION_CHECK).passed());
    }
}
