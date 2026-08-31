package com.titanium.policy.application.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.titanium.metadata.enums.billing.PremiumCollectionMode;
import com.titanium.metadata.enums.policy.IssuanceStrategy;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.IssuanceMode;
import com.titanium.metadata.enums.product.ProductEnum.PaymentFrequency;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.metadata.enums.product.ProductEnum.SalesChannel;
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.application.exception.CustomerResolutionException;
import com.titanium.policy.application.exception.IssuanceOrchestrationException;
import com.titanium.policy.application.orchestration.issuance.IssuanceCustomerResolver;
import com.titanium.policy.application.orchestration.issuance.orchestrator.IssuanceOrchestrator;
import com.titanium.policy.common.enums.IssuanceStage;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.port.ProductServicePort;
import com.titanium.policy.query.repository.InsuranceViewRepository;
import com.titanium.policy.query.repository.IssuanceProgressViewRepository;
import com.titanium.policy.query.repository.PolicyViewRepository;
import com.titanium.policy.query.repository.ProposalViewRepository;
import com.titanium.policy.query.view.InsuranceView;
import com.titanium.policy.query.view.IssuanceProgressView;
import com.titanium.policy.query.view.PolicyView;
import com.titanium.policy.query.view.ProposalView;
import com.titanium.policy.service.IssuanceEligibilityDomainService;
import com.titanium.policy.valueobject.IssuancePlanLine;
import com.titanium.policy.valueobject.IssuanceProcessConfig;
import com.titanium.policy.valueobject.IssuanceRequest;
import com.titanium.policy.valueobject.IssuanceResult;
import com.titanium.policy.valueobject.RuleDecision;
import com.titanium.policy.valueobject.product.ProductIssueRules;

class PolicyIssuanceApplicationServiceTest {

    private static final String TENANT_ID = "TENANT_001";
    private static final String BIZ_NO = "BIZ_001";
    private static final String PRODUCT_ID = "PRODUCT_001";

    private IssuanceOrchestrator issuanceOrchestrator;
    private IssuanceEligibilityDomainService eligibilityDomainService;
    private ProductServicePort productServicePort;
    private IssuanceProgressBaselineWriter progressBaselineWriter;
    private IssuanceProgressViewRepository progressRepository;
    private ProposalViewRepository proposalRepository;
    private InsuranceViewRepository insuranceRepository;
    private PolicyViewRepository policyRepository;
    private IssuanceCustomerResolver customerResolver;
    private PolicyIssuanceApplicationService service;
    private IssuanceRequest request;

    @BeforeEach
    void setUp() {
        issuanceOrchestrator = mock(IssuanceOrchestrator.class);
        eligibilityDomainService = mock(IssuanceEligibilityDomainService.class);
        productServicePort = mock(ProductServicePort.class);
        progressRepository = mock(IssuanceProgressViewRepository.class);
        proposalRepository = mock(ProposalViewRepository.class);
        insuranceRepository = mock(InsuranceViewRepository.class);
        policyRepository = mock(PolicyViewRepository.class);
        progressBaselineWriter = mock(IssuanceProgressBaselineWriter.class);
        customerResolver = mock(IssuanceCustomerResolver.class);
        service = new PolicyIssuanceApplicationService(issuanceOrchestrator, eligibilityDomainService,
                productServicePort, progressRepository, progressBaselineWriter, customerResolver,
                proposalRepository, insuranceRepository, policyRepository);
        request = request();
        when(progressRepository.findByBizNoAndTenantId(BIZ_NO, TENANT_ID)).thenReturn(Optional.empty());
        when(customerResolver.resolve(request)).thenReturn(request);
        when(productServicePort.getIssuanceMode(PRODUCT_ID, TENANT_ID)).thenReturn(IssuanceMode.THREE_STEP);
    }

    @Test
    void rejectsIssuanceWhenProductRuleServiceFails() {
        when(productServicePort.getIssueRules(PRODUCT_ID, TENANT_ID))
                .thenThrow(new IllegalStateException("product service unavailable"));

        IssuanceResult result = service.submitIssuance(request);

        assertProductRulesUnavailable(result);
    }

    @Test
    void rejectsIssuanceWhenProductRulesAreMissing() {
        when(productServicePort.getIssueRules(PRODUCT_ID, TENANT_ID)).thenReturn(null);

        IssuanceResult result = service.submitIssuance(request);

        assertProductRulesUnavailable(result);
    }

    @Test
    void transientCustomerFailureDoesNotPersistRejectedBaselineAndSameBizCanRetry() {
        CustomerResolutionException failure = new CustomerResolutionException(
                PolicyErrorCode.ISSUANCE_CUSTOMER_RESOLUTION_FAILED, "客户服务不可用", new IllegalStateException(), true);
        when(customerResolver.resolve(request)).thenThrow(failure, failure);

        IssuanceResult first = service.submitIssuance(request);
        IssuanceResult retry = service.submitIssuance(request);

        assertEquals(PolicyErrorCode.ISSUANCE_CUSTOMER_RESOLUTION_FAILED.getCode(), first.rejectCode());
        assertEquals(PolicyErrorCode.ISSUANCE_CUSTOMER_RESOLUTION_FAILED.getCode(), retry.rejectCode());
        verify(progressBaselineWriter, never()).save(any(), any());
        verify(customerResolver, org.mockito.Mockito.times(2)).resolve(request);
    }

    @Test
    void concurrentBaselineConflictReturnsFirstPersistedResultWithoutOrchestration() {
        IssuanceProgressView existing = acceptedProgress();
        when(progressRepository.findByBizNoAndTenantId(BIZ_NO, TENANT_ID))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(productServicePort.getIssueRules(PRODUCT_ID, TENANT_ID)).thenReturn(unrestrictedRules());
        when(eligibilityDomainService.validate(eq(request), any())).thenReturn(RuleDecision.accepted());
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("uk_issuance_biz"))
                .when(progressBaselineWriter).save(eq(request), any());

        IssuanceResult result = service.submitIssuance(request);

        assertEquals(IssuanceStage.ACCEPTED, result.currentStage());
        assertEquals(BIZ_NO, result.bizNo());
        verify(issuanceOrchestrator, never()).orchestrate(any(IssuanceProcessConfig.class),
                any(IssuanceRequest.class));
    }

    @Test
    void successfulOrchestrationDoesNotCompeteWithEventProjectionOrOverwriteProgressAsRejected() {
        IssuanceResult orchestrated = successfulResult();
        IssuanceProgressView staleBaseline = acceptedProgress();
        IssuanceProgressView projectedProgress = acceptedProgress();
        projectedProgress.setCurrentStage(IssuanceStage.POLICY_ISSUED.getCode());
        projectedProgress.setPolicyId("POLICY_001");
        when(progressRepository.findByBizNoAndTenantId(BIZ_NO, TENANT_ID))
                .thenReturn(Optional.empty(), Optional.of(staleBaseline), Optional.of(projectedProgress));
        when(productServicePort.getIssueRules(PRODUCT_ID, TENANT_ID)).thenReturn(unrestrictedRules());
        when(eligibilityDomainService.validate(eq(request), any())).thenReturn(RuleDecision.accepted());
        when(issuanceOrchestrator.orchestrate(IssuanceProcessConfig.threeStep(PRODUCT_ID), request))
                .thenReturn(orchestrated);
        when(progressRepository.save(any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(IssuanceProgressView.class,
                        TENANT_ID + "_" + BIZ_NO))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IssuanceResult result = service.submitIssuance(request);

        assertSame(orchestrated, result);
        assertEquals(IssuanceStage.POLICY_ISSUED.getCode(), projectedProgress.getCurrentStage());
        verify(progressRepository, never()).save(any());
    }

    @Test
    void acceptedBaselinePersistsProductIssuanceMode() {
        when(productServicePort.getIssueRules(PRODUCT_ID, TENANT_ID)).thenReturn(unrestrictedRules());
        when(productServicePort.getIssuanceMode(PRODUCT_ID, TENANT_ID)).thenReturn(IssuanceMode.THREE_STEP);
        when(eligibilityDomainService.validate(eq(request), any())).thenReturn(RuleDecision.accepted());
        when(issuanceOrchestrator.orchestrate(IssuanceProcessConfig.threeStep(PRODUCT_ID), request))
                .thenReturn(successfulResult());

        service.submitIssuance(request);

        ArgumentCaptor<IssuanceResult> baselineCaptor = ArgumentCaptor.forClass(IssuanceResult.class);
        verify(progressBaselineWriter).save(eq(request), baselineCaptor.capture());
        assertEquals(IssuanceMode.THREE_STEP, baselineCaptor.getValue().issuanceMode());
        verify(issuanceOrchestrator).orchestrate(IssuanceProcessConfig.threeStep(PRODUCT_ID), request);
    }

    @Test
    void progressQueryEnrichesDocumentNumbersAndIssuedPolicyDetails() {
        IssuanceProgressView progress = acceptedProgress();
        progress.setCurrentStage(IssuanceStage.PENDING_COLLECTION.getCode());
        progress.setProposalId("PROPOSAL_001");
        progress.setInsuranceId("INSURANCE_001");
        progress.setPolicyId("POLICY_001");
        progress.setStandardPremium(new BigDecimal("625.00"));
        progress.setPayablePremium(new BigDecimal("750.00"));
        when(progressRepository.findByBizNoAndTenantId(BIZ_NO, TENANT_ID)).thenReturn(Optional.of(progress));

        ProposalView proposal = new ProposalView();
        proposal.setProposalNo("PRP202608140000001");
        when(proposalRepository.findByProposalIdAndTenantId("PROPOSAL_001", TENANT_ID))
                .thenReturn(Optional.of(proposal));

        InsuranceView insurance = new InsuranceView();
        insurance.setInsuranceNo("INS202608140000001");
        when(insuranceRepository.findByInsuranceIdAndTenantId("INSURANCE_001", TENANT_ID))
                .thenReturn(Optional.of(insurance));

        PolicyView policy = new PolicyView();
        policy.setPolicyNo("POL202608140000001");
        policy.setPolicyStatus(com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus.PENDING_EFFECTIVE);
        policy.setLineCount(1);
        policy.setTotalPremium(new BigDecimal("750.00"));
        when(policyRepository.findByPolicyIdAndTenantId("POLICY_001", TENANT_ID)).thenReturn(Optional.of(policy));

        IssuanceResult result = service.getIssuanceProgress(BIZ_NO, TENANT_ID).orElseThrow();

        assertEquals("PRP202608140000001", result.proposalNo());
        assertEquals("INS202608140000001", result.insuranceNo());
        assertEquals("POL202608140000001", result.policies().get(0).policyNo());
        assertEquals("PENDING_EFFECTIVE", result.policies().get(0).policyStatus());
        assertEquals(new BigDecimal("750.00"), result.policies().get(0).totalPremium().value());
        assertEquals(new BigDecimal("625.00"), result.standardPremium().value());
        assertEquals(new BigDecimal("125.00"), result.extraPremium().value());
        assertEquals(new BigDecimal("750.00"), result.payablePremium().value());
    }

    @Test
    void unexpectedOrchestrationFailureIsPropagatedWithoutWritingRejectedProgress() {
        IllegalStateException failure = new IllegalStateException("command gateway unavailable");
        when(productServicePort.getIssueRules(PRODUCT_ID, TENANT_ID)).thenReturn(unrestrictedRules());
        when(eligibilityDomainService.validate(eq(request), any())).thenReturn(RuleDecision.accepted());
        when(issuanceOrchestrator.orchestrate(IssuanceProcessConfig.threeStep(PRODUCT_ID), request))
                .thenThrow(failure);

        IllegalStateException actual = assertThrows(IllegalStateException.class,
                () -> service.submitIssuance(request));

        assertSame(failure, actual);
        verify(progressBaselineWriter, times(1)).save(eq(request), any(IssuanceResult.class));
        verify(progressBaselineWriter).releaseIfUntouched(request);
        verify(progressRepository, never()).save(any());
    }

    @Test
    void synchronousOrchestrationRejectionMarksUntouchedBaselineRejected() {
        IssuanceResult rejected = IssuanceResult.rejected(BIZ_NO,
                RuleDecision.rejected(PolicyErrorCode.ISSUANCE_RISK_REJECTED, "基础自动核保"));
        when(productServicePort.getIssueRules(PRODUCT_ID, TENANT_ID)).thenReturn(unrestrictedRules());
        when(eligibilityDomainService.validate(eq(request), any())).thenReturn(RuleDecision.accepted());
        when(issuanceOrchestrator.orchestrate(IssuanceProcessConfig.threeStep(PRODUCT_ID), request))
                .thenReturn(rejected);

        IssuanceResult result = service.submitIssuance(request);

        assertSame(rejected, result);
        verify(progressBaselineWriter).markRejectedIfUntouched(request, rejected);
        verify(progressBaselineWriter, never()).releaseIfUntouched(any());
    }

    @Test
    void orchestrationFailureWithoutPartialResultReleasesUntouchedBaseline() {
        IssuanceOrchestrationException failure = new IssuanceOrchestrationException("首个命令执行失败", null,
                new IllegalStateException("command gateway unavailable"));
        when(productServicePort.getIssueRules(PRODUCT_ID, TENANT_ID)).thenReturn(unrestrictedRules());
        when(eligibilityDomainService.validate(eq(request), any())).thenReturn(RuleDecision.accepted());
        when(issuanceOrchestrator.orchestrate(IssuanceProcessConfig.threeStep(PRODUCT_ID), request))
                .thenThrow(failure);

        IssuanceOrchestrationException actual = assertThrows(IssuanceOrchestrationException.class,
                () -> service.submitIssuance(request));

        assertSame(failure, actual);
        verify(progressBaselineWriter).releaseIfUntouched(request);
        verify(progressBaselineWriter, never()).markRejectedIfUntouched(any(), any());
    }

    @Test
    void orchestrationFailureWithPartialResultPreservesProgressAndCreatedDocuments() {
        IssuanceResult partialResult = successfulResult();
        IssuanceOrchestrationException failure = new IssuanceOrchestrationException("后续命令执行失败", partialResult,
                new IllegalStateException("command gateway unavailable"));
        when(productServicePort.getIssueRules(PRODUCT_ID, TENANT_ID)).thenReturn(unrestrictedRules());
        when(eligibilityDomainService.validate(eq(request), any())).thenReturn(RuleDecision.accepted());
        when(issuanceOrchestrator.orchestrate(IssuanceProcessConfig.threeStep(PRODUCT_ID), request))
                .thenThrow(failure);

        IssuanceResult result = service.submitIssuance(request);

        assertSame(partialResult, result);
        verify(progressBaselineWriter, never()).releaseIfUntouched(any());
        verify(progressBaselineWriter, never()).markRejectedIfUntouched(any(), any());
    }

    private void assertProductRulesUnavailable(IssuanceResult result) {
        assertFalse(result.success());
        assertEquals(IssuanceStage.REJECTED, result.currentStage());
        assertEquals(PolicyErrorCode.ISSUANCE_PRODUCT_RULES_UNAVAILABLE.getCode(), result.rejectCode());
        verify(progressBaselineWriter).save(eq(request), any(IssuanceResult.class));
        verify(eligibilityDomainService, never()).validate(any(), any());
        verify(issuanceOrchestrator, never()).orchestrate(any(IssuanceProcessConfig.class),
                any(IssuanceRequest.class));
    }

    private IssuanceRequest request() {
        InsuredPartyList.HolderInfo holder = new InsuredPartyList.HolderInfo("CUSTOMER_HOLDER", "HOLDER_001",
                "张三", null, null, null);
        InsuredPartyList.InsuredInfo insured = new InsuredPartyList.InsuredInfo("CUSTOMER_INSURED", "INSURED_001",
                "李四", null, null, 35, null, null);
        InsuredPartyList parties = new InsuredPartyList("PARTIES_001", holder, List.of(insured), List.of());
        IssuancePlanLine main = new IssuancePlanLine(1, PRODUCT_ID, ProductCategory.MAIN, null,
                Money.of(new BigDecimal("500000"), "CNY"), null, null, PaymentFrequency.ANNUAL, 20, List.of(),
                null);
        return new IssuanceRequest(BIZ_NO, TENANT_ID, "USER_001", "PACKAGE_001",
                IssuanceStrategy.MERGE_ONE_POLICY, "CUSTOMER_HOLDER", parties, PolicyForm.INDIVIDUAL, null,
                LocalDateTime.now(), LocalDateTime.now().plusYears(1), PremiumCollectionMode.ONLINE, "CHANNEL_001",
                SalesChannel.ONLINE, "AGENT_001", List.of(main), Money.of(new BigDecimal("1000"), "CNY"), null);
    }

    private ProductIssueRules unrestrictedRules() {
        return new ProductIssueRules(null, null, null, null, null, null, List.of(), List.of(), List.of(), null, null,
                null, false, List.of(), List.of(), null, List.of(), null, null, false, false);
    }

    private IssuanceProgressView acceptedProgress() {
        IssuanceProgressView view = new IssuanceProgressView();
        view.setBizNo(BIZ_NO);
        view.setTenantId(TENANT_ID);
        view.setCurrentStage(IssuanceStage.ACCEPTED.getCode());
        view.setIssuanceStrategy(IssuanceStrategy.MERGE_ONE_POLICY.getCode());
        view.setLineCount(1);
        return view;
    }

    private IssuanceResult successfulResult() {
        IssuanceResult.IssuedPolicy policy = new IssuanceResult.IssuedPolicy("POLICY_001", "POLICY_NO_001",
                PRODUCT_ID, 1, Money.of(new BigDecimal("1000"), "CNY"));
        return new IssuanceResult(true, BIZ_NO, null, IssuanceStrategy.MERGE_ONE_POLICY,
                IssuanceStage.POLICY_ISSUED, null, null, "INSURANCE_001", null, List.of(policy), null,
                Money.of(new BigDecimal("1000"), "CNY"), null, Money.of(new BigDecimal("1000"), "CNY"),
                null, null, null, null, null);
    }
}
