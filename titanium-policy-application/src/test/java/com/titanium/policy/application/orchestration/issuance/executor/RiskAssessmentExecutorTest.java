package com.titanium.policy.application.orchestration.issuance.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.metadata.enums.billing.PremiumCollectionMode;
import com.titanium.metadata.enums.policy.IssuanceStrategy;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.PaymentFrequency;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.metadata.enums.product.ProductEnum.SalesChannel;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.common.enums.RiskAssessmentStep;
import com.titanium.policy.common.enums.RuleEngineDecision;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.port.RuleEngineServicePort;
import com.titanium.policy.service.RiskAssessmentDomainService;
import com.titanium.policy.service.impl.RiskAssessmentDomainServiceImpl;
import com.titanium.policy.valueobject.IssuancePlanLine;
import com.titanium.policy.valueobject.IssuanceRequest;
import com.titanium.policy.valueobject.RiskAssessmentDecision;

class RiskAssessmentExecutorTest {

    private static final String TENANT_ID = "TENANT_001";

    private RuleEngineServicePort ruleEngineServicePort;

    private RiskAssessmentDomainService riskAssessmentDomainService;

    private IssuanceRequest request;

    private RiskAssessmentExecutor executor;

    @BeforeEach
    void setUp() {
        ruleEngineServicePort = mock(RuleEngineServicePort.class);
        riskAssessmentDomainService = mock(RiskAssessmentDomainService.class);
        request = new IssuanceRequest("BIZ_001", TENANT_ID, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
        executor = new RiskAssessmentExecutor(ruleEngineServicePort, riskAssessmentDomainService);
    }

    @Test
    void usesExplicitRuleEngineRejectionInsteadOfCallSuccess() {
        RiskAssessmentStep step = RiskAssessmentStep.BASIC_UNDERWRITING;
        when(riskAssessmentDomainService.requiresRuleEngine(step)).thenReturn(true);
        when(ruleEngineServicePort.executeRule(eq(step.getCode()), anyMap(), eq(TENANT_ID)))
                .thenReturn(RuleEngineDecision.REJECT);
        stubRuleEngineDecision(step);

        assertFalse(executor.execute(step, request));
    }

    @Test
    void referDecisionContinuesToDownstreamUnderwriting() {
        RiskAssessmentStep step = RiskAssessmentStep.BASIC_UNDERWRITING;
        RiskAssessmentDomainService domainService = new RiskAssessmentDomainServiceImpl();
        executor = new RiskAssessmentExecutor(ruleEngineServicePort, domainService);
        when(ruleEngineServicePort.executeRule(eq(step.getCode()), anyMap(), eq(TENANT_ID)))
                .thenReturn(RuleEngineDecision.REFER);

        assertTrue(executor.execute(step, request));
    }

    @Test
    void sendsScalarAmountsAndInsuredAgesToRuleEngine() {
        RiskAssessmentStep step = RiskAssessmentStep.BASIC_UNDERWRITING;
        request = requestWithAmounts();
        when(riskAssessmentDomainService.requiresRuleEngine(step)).thenReturn(true);
        when(ruleEngineServicePort.executeRule(eq(step.getCode()), anyMap(), eq(TENANT_ID)))
                .thenReturn(RuleEngineDecision.PASS);
        when(riskAssessmentDomainService.judge(step, RuleEngineDecision.PASS))
                .thenReturn(RiskAssessmentDecision.pass(step));

        assertTrue(executor.execute(step, request));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.forClass(Map.class);
        verify(ruleEngineServicePort).executeRule(eq(step.getCode()), variables.capture(), eq(TENANT_ID));
        assertEquals(0, new BigDecimal("500000").compareTo((BigDecimal) variables.getValue().get("sumInsured")));
        assertEquals(0, new BigDecimal("1000").compareTo((BigDecimal) variables.getValue().get("quotedPremium")));
        assertEquals(List.of(35), variables.getValue().get("insuredAges"));
    }

    @Test
    void propagatesRuleEngineFailureInsteadOfMappingItToBusinessRejection() {
        RiskAssessmentStep step = RiskAssessmentStep.BASIC_UNDERWRITING;
        IllegalStateException failure = new IllegalStateException("RULE_SET_NOT_FOUND");
        when(riskAssessmentDomainService.requiresRuleEngine(step)).thenReturn(true);
        when(ruleEngineServicePort.executeRule(eq(step.getCode()), anyMap(), eq(TENANT_ID))).thenThrow(failure);
        stubRuleEngineDecision(step);

        IllegalStateException actual = assertThrows(IllegalStateException.class,
                () -> executor.execute(step, request));

        assertSame(failure, actual);
    }

    private void stubRuleEngineDecision(RiskAssessmentStep step) {
        when(riskAssessmentDomainService.judge(eq(step), any(RuleEngineDecision.class))).thenAnswer(invocation -> {
            RuleEngineDecision decision = invocation.getArgument(1);
            return decision == RuleEngineDecision.REJECT
                    ? RiskAssessmentDecision.reject(step, "规则引擎裁决拒绝")
                    : RiskAssessmentDecision.pass(step);
        });
    }

    private IssuanceRequest requestWithAmounts() {
        InsuredPartyList.HolderInfo holder = new InsuredPartyList.HolderInfo("CUSTOMER_HOLDER", "HOLDER_001",
                "张三", null, null, null);
        InsuredPartyList.InsuredInfo insured = new InsuredPartyList.InsuredInfo("CUSTOMER_INSURED", "INSURED_001",
                "李四", null, null, 35, null, null);
        InsuredPartyList parties = new InsuredPartyList("PARTIES_001", holder, List.of(insured), List.of());
        IssuancePlanLine main = new IssuancePlanLine(1, "PRODUCT_001", ProductCategory.MAIN, null,
                Money.of(new BigDecimal("500000"), "CNY"), null, null, PaymentFrequency.ANNUAL, 20, List.of(),
                null);
        return new IssuanceRequest("BIZ_001", TENANT_ID, "USER_001", "PACKAGE_001",
                IssuanceStrategy.MERGE_ONE_POLICY, "CUSTOMER_HOLDER", parties, PolicyForm.INDIVIDUAL, null,
                LocalDateTime.now(), LocalDateTime.now().plusYears(1), PremiumCollectionMode.ONLINE, "CHANNEL_001",
                SalesChannel.ONLINE, "AGENT_001", List.of(main), Money.of(new BigDecimal("1000"), "CNY"), null);
    }
}
