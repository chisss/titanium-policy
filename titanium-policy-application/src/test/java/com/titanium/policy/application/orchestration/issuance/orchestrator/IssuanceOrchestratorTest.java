package com.titanium.policy.application.orchestration.issuance.orchestrator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.titanium.common.exception.BusinessException;
import com.titanium.metadata.enums.billing.PremiumCollectionMode;
import com.titanium.metadata.enums.customer.CustomerEnum.CustomerGender;
import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.IssuanceStrategy;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.IssuanceMode;
import com.titanium.metadata.enums.product.ProductEnum.PaymentFrequency;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.metadata.enums.product.ProductEnum.SalesChannel;
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.application.exception.IssuanceOrchestrationException;
import com.titanium.policy.application.orchestration.issuance.service.InsuranceLinePremiumConfirmationService;
import com.titanium.policy.application.orchestration.issuance.assembler.InsuranceLineAssembler;
import com.titanium.policy.application.orchestration.issuance.assembler.PolicyProductAssembler;
import com.titanium.policy.application.orchestration.issuance.assembler.ProposalLineAssembler;
import com.titanium.policy.application.orchestration.issuance.executor.RiskAssessmentExecutor;
import com.titanium.policy.application.support.TestPolicyNoGenerator;
import com.titanium.policy.command.CreateInsuranceDirectlyCommand;
import com.titanium.policy.command.CreatePolicyDirectlyCommand;
import com.titanium.policy.command.CreateProposalCommand;
import com.titanium.policy.command.SubmitProposalCommand;
import com.titanium.policy.command.SubmitUnderwritingCommand;
import com.titanium.policy.common.enums.IssuanceStage;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.port.BillingServicePort;
import com.titanium.policy.port.ConfirmedPremiumPricingPort;
import com.titanium.policy.port.ProductServicePort;
import com.titanium.policy.valueobject.IssuancePlanLine;
import com.titanium.policy.valueobject.IssuanceProcessConfig;
import com.titanium.policy.valueobject.IssuanceRequest;
import com.titanium.policy.valueobject.IssuanceResult;
import com.titanium.policy.valueobject.billing.PremiumScheduleRequest;
import com.titanium.policy.valueobject.policy.CollectionResult;
import com.titanium.policy.valueobject.pricing.ConfirmedPremiumResult;
import com.titanium.policy.valueobject.product.ProductIssueRules;

/**
 * 验证统一出单起点命令的顺序与关键字段透传。
 */
class IssuanceOrchestratorTest {

    private static final String TENANT_ID = "TENANT_001";
    private static final String BIZ_NO = "BIZ_001";
    private static final String PRODUCT_ID = "PRODUCT_001";

    private CommandGateway commandGateway;
    private ProductServicePort productServicePort;
    private ConfirmedPremiumPricingPort confirmedPremiumPricingPort;
    private BillingServicePort billingServicePort;
    private RiskAssessmentExecutor riskAssessmentExecutor;
    private PremiumCollectionOrchestrator premiumCollectionOrchestrator;
    private IssuanceOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        commandGateway = mock(CommandGateway.class);
        productServicePort = mock(ProductServicePort.class);
        confirmedPremiumPricingPort = mock(ConfirmedPremiumPricingPort.class);
        billingServicePort = mock(BillingServicePort.class);
        riskAssessmentExecutor = mock(RiskAssessmentExecutor.class);
        premiumCollectionOrchestrator = mock(PremiumCollectionOrchestrator.class);
        InsuranceLineAssembler insuranceLineAssembler = new InsuranceLineAssembler(productServicePort);
        ProposalLineAssembler proposalLineAssembler = new ProposalLineAssembler(productServicePort);
        PolicyProductAssembler policyProductAssembler = new PolicyProductAssembler(productServicePort, null,
                insuranceLineAssembler);
        orchestrator = new IssuanceOrchestrator(commandGateway, new TestPolicyNoGenerator(), riskAssessmentExecutor,
                productServicePort, insuranceLineAssembler, policyProductAssembler, proposalLineAssembler, null, null, null,
                new InsuranceLinePremiumConfirmationService(confirmedPremiumPricingPort,
                        new com.titanium.policy.application.orchestration.issuance.ConfirmedPremiumRequestValidator(),
                        new com.titanium.policy.application.orchestration.issuance.assembler.ConfirmedPremiumRequestAssembler()),
                premiumCollectionOrchestrator,
                new PremiumScheduleOrchestrator(billingServicePort));
        when(riskAssessmentExecutor.execute(any(), any())).thenReturn(true);
        when(productServicePort.getProductBasicInfo(PRODUCT_ID, TENANT_ID))
                .thenReturn(new com.titanium.policy.valueobject.product.ProductBasicInfo(PRODUCT_ID, "P001", "测试产品",
                        "V1", null, "EFFECTIVE"));
        when(productServicePort.getIssueRules(PRODUCT_ID, TENANT_ID)).thenReturn(issueRules());
        when(confirmedPremiumPricingPort.confirm(any()))
                .thenReturn(new ConfirmedPremiumResult(
                        "CALC_001", "CONFIRMED", "ISSUANCE_CONFIRM", PRODUCT_ID, "V1", "CNY",
                        new BigDecimal("1000.00"), new BigDecimal("1000.00"), "PP_V1", "HASH_001"));
    }

    @Test
    void twoStepCreatesInsuranceThenImmediatelySubmitsUnderwriting() {
        IssuanceResult result = orchestrator.orchestrate(IssuanceProcessConfig.twoStep(PRODUCT_ID), request());

        ArgumentCaptor<Object> commands = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway, org.mockito.Mockito.times(2)).sendAndWait(commands.capture());
        List<Object> dispatched = commands.getAllValues();
        assertInstanceOf(CreateInsuranceDirectlyCommand.class, dispatched.get(0));
        assertInstanceOf(SubmitUnderwritingCommand.class, dispatched.get(1));
        CreateInsuranceDirectlyCommand create = (CreateInsuranceDirectlyCommand) dispatched.get(0);
        SubmitUnderwritingCommand submit = (SubmitUnderwritingCommand) dispatched.get(1);
        assertEquals(BIZ_NO, create.bizNo());
        assertEquals(TENANT_ID, create.tenantId());
        assertEquals(0, new BigDecimal("500000").compareTo(create.sumInsured()));
        assertEquals("ANNUAL", create.paymentMode());
        assertEquals(20, create.premiumPaymentYears());
        assertEquals(create.insuranceId(), submit.insuranceId());
        assertEquals(TENANT_ID, submit.tenantId());
        assertEquals(IssuanceMode.TWO_STEP, result.issuanceMode());
        verifyNoMoreInteractions(commandGateway);
    }

    @Test
    void threeStepCreatesProposalThenImmediatelySubmitsProposal() {
        when(productServicePort.getProductBasicInfo(PRODUCT_ID, TENANT_ID))
                .thenReturn(new com.titanium.policy.valueobject.product.ProductBasicInfo(PRODUCT_ID, "P001",
                        "测试产品", "V1", InsuranceProductType.TERM_LIFE, "EFFECTIVE"));

        IssuanceResult result = orchestrator.orchestrate(IssuanceProcessConfig.threeStep(PRODUCT_ID), request());

        ArgumentCaptor<Object> commands = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway, org.mockito.Mockito.times(2)).sendAndWait(commands.capture());
        List<Object> dispatched = commands.getAllValues();
        assertInstanceOf(CreateProposalCommand.class, dispatched.get(0));
        assertInstanceOf(SubmitProposalCommand.class, dispatched.get(1));
        CreateProposalCommand create = (CreateProposalCommand) dispatched.get(0);
        SubmitProposalCommand submit = (SubmitProposalCommand) dispatched.get(1);
        assertEquals(BIZ_NO, create.bizNo());
        assertEquals(TENANT_ID, create.tenantId());
        assertNotNull(create.insuredPartyList());
        assertEquals(PremiumCollectionMode.ONLINE, create.collectionMode());
        assertEquals("P001", create.expectedProductCode());
        assertEquals(InsuranceProductType.TERM_LIFE, create.insuranceType());
        assertEquals("ANNUAL", create.paymentMode());
        assertEquals(20, create.premiumPaymentYears());
        assertEquals(create.proposalId(), submit.proposalId());
        assertEquals(TENANT_ID, submit.tenantId());
        assertEquals(IssuanceMode.THREE_STEP, result.issuanceMode());
        verifyNoMoreInteractions(commandGateway);
    }

    @Test
    void failedUnderwritingSubmissionPreservesCreatedInsurance() {
        when(commandGateway.sendAndWait(any(SubmitUnderwritingCommand.class)))
                .thenThrow(new IllegalStateException("command bus unavailable"));

        IssuanceOrchestrationException exception = assertThrows(IssuanceOrchestrationException.class,
                () -> orchestrator.orchestrate(IssuanceProcessConfig.twoStep(PRODUCT_ID), request()));

        assertEquals(IssuanceStage.INSURANCE_CREATED, exception.partialResult().currentStage());
        assertEquals(TENANT_ID, request().tenantId());
        verify(commandGateway).sendAndWait(any(CreateInsuranceDirectlyCommand.class));
    }

    @Test
    void callerQuoteIsNotTreatedAsConfirmedInsuranceLinePremium() {
        IssuanceRequest request = requestWithRider();

        orchestrator.orchestrate(IssuanceProcessConfig.twoStep(PRODUCT_ID), request);
        ArgumentCaptor<Object> twoStepCommands = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway, org.mockito.Mockito.times(2)).sendAndWait(twoStepCommands.capture());
        CreateInsuranceDirectlyCommand insurance = (CreateInsuranceDirectlyCommand) twoStepCommands.getAllValues()
                .get(0);
        assertNull(insurance.insuranceLines().get(0).premium());
        assertNull(insurance.insuranceLines().get(1).premium());

        org.mockito.Mockito.clearInvocations(commandGateway);
        orchestrator.orchestrate(IssuanceProcessConfig.threeStep(PRODUCT_ID), request);
        ArgumentCaptor<Object> threeStepCommands = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway, org.mockito.Mockito.times(2)).sendAndWait(threeStepCommands.capture());
        CreateProposalCommand proposal = (CreateProposalCommand) threeStepCommands.getAllValues().get(0);
        assertEquals(request.quotedPremium(), proposal.proposalLines().get(0).intendedPremium());
        assertNull(proposal.proposalLines().get(1).intendedPremium());
    }

    @Test
    void oneStepUsesSystemPremiumBeforeCreatingPolicyAndCollection() {
        when(productServicePort.getClauseRefs(PRODUCT_ID, TENANT_ID)).thenReturn(List.of());
        when(premiumCollectionOrchestrator.collect(
                any(), any(), any(), any(), any(LocalDate.class), any(), anyList()))
                .thenReturn(CollectionResult.pending("BILL_001", "ACCOUNT_001", "PAY_001", "credential"));

        IssuanceResult result = orchestrator.orchestrate(IssuanceProcessConfig.oneStep(PRODUCT_ID), request());

        assertEquals(IssuanceStage.PENDING_COLLECTION, result.currentStage());
        assertEquals("BILL_001", result.billId());
        assertEquals("PAY_001", result.paymentOrderId());
        ArgumentCaptor<CreatePolicyDirectlyCommand> command = ArgumentCaptor.forClass(
                CreatePolicyDirectlyCommand.class);
        verify(commandGateway).sendAndWait(command.capture());
        assertEquals(Money.of(new BigDecimal("1000.00"), "CNY"), command.getValue().totalPremium());
        assertEquals(Money.of(new BigDecimal("1000.00"), "CNY"),
                command.getValue().policyProducts().get(0).premium());
        verify(confirmedPremiumPricingPort).confirm(any());
        verify(premiumCollectionOrchestrator).collect(eq(result.firstPolicyId()), eq("CUSTOMER_HOLDER"),
                eq(Money.of(new BigDecimal("1000.00"), "CNY")), eq(PremiumCollectionMode.ONLINE),
                eq(request().periodStart().toLocalDate()), eq(TENANT_ID), anyList());
    }

    @Test
    void oneStepTreatsMissingOptionalPeriodConfigAsZero() {
        when(productServicePort.getClauseRefs(PRODUCT_ID, TENANT_ID)).thenReturn(List.of());
        when(productServicePort.getIssueRules(PRODUCT_ID, TENANT_ID))
                .thenReturn(new ProductIssueRules(0, 65, null, null, null, null, List.of(), List.of(), List.of(),
                        null, null, null, false, List.of(), List.of(), null, List.of(), null, null, false, false));
        when(premiumCollectionOrchestrator.collect(any(), any(), any(), any(), any(LocalDate.class), any(), anyList()))
                .thenReturn(CollectionResult.pending("BILL_001", "ACCOUNT_001", "PAY_001", "credential"));

        IssuanceResult result = orchestrator.orchestrate(IssuanceProcessConfig.oneStep(PRODUCT_ID), request());

        assertEquals(IssuanceStage.PENDING_COLLECTION, result.currentStage());
        ArgumentCaptor<CreatePolicyDirectlyCommand> command = ArgumentCaptor.forClass(CreatePolicyDirectlyCommand.class);
        verify(commandGateway).sendAndWait(command.capture());
        assertEquals(0, command.getValue().policyPeriod().waitingPeriodDays());
        assertEquals(0, command.getValue().policyPeriod().hesitationPeriodDays());
    }

    @Test
    void oneStepGeneratesAnnualScheduleAfterBillCreation() {
        when(productServicePort.getClauseRefs(PRODUCT_ID, TENANT_ID)).thenReturn(List.of());
        when(premiumCollectionOrchestrator.collect(
                any(), any(), any(), any(), any(LocalDate.class), any(), anyList()))
                .thenReturn(CollectionResult.pending("BILL_001", "ACCOUNT_001", "PAY_001", "credential"));

        IssuanceResult result = orchestrator.orchestrate(IssuanceProcessConfig.oneStep(PRODUCT_ID), request());

        ArgumentCaptor<PremiumScheduleRequest> scheduleCaptor = ArgumentCaptor.forClass(PremiumScheduleRequest.class);
        InOrder billingOrder = inOrder(premiumCollectionOrchestrator, billingServicePort);
        billingOrder.verify(premiumCollectionOrchestrator).collect(eq(result.firstPolicyId()),
                eq("CUSTOMER_HOLDER"), eq(Money.of(new BigDecimal("1000.00"), "CNY")),
                eq(PremiumCollectionMode.ONLINE), eq(request().periodStart().toLocalDate()), eq(TENANT_ID), anyList());
        billingOrder.verify(billingServicePort).generatePremiumSchedule(scheduleCaptor.capture());
        PremiumScheduleRequest schedule = scheduleCaptor.getValue();
        assertEquals(result.firstPolicyId(), schedule.policyId());
        assertEquals("ACCOUNT_001", schedule.billingAccountId());
        assertEquals("ANNUAL", schedule.paymentMode());
        assertEquals(20, schedule.totalPeriods());
        assertEquals(new BigDecimal("50.00"), schedule.installmentAmount());
        assertEquals(request().periodStart().toLocalDate(), schedule.firstDueDate());
        assertEquals(TENANT_ID, schedule.tenantId());
    }

    @Test
    void oneStepScheduleFailureDoesNotDestroyCreatedPolicy() {
        when(productServicePort.getClauseRefs(PRODUCT_ID, TENANT_ID)).thenReturn(List.of());
        when(premiumCollectionOrchestrator.collect(
                any(), any(), any(), any(), any(LocalDate.class), any(), anyList()))
                .thenReturn(CollectionResult.pending("BILL_001", "ACCOUNT_001", "PAY_001", "credential"));
        org.mockito.Mockito.doThrow(new IllegalStateException("billing unavailable"))
                .when(billingServicePort).generatePremiumSchedule(any());

        IssuanceResult result = orchestrator.orchestrate(IssuanceProcessConfig.oneStep(PRODUCT_ID), request());

        assertEquals(IssuanceStage.PENDING_COLLECTION, result.currentStage());
        assertNotNull(result.firstPolicyId());
        verify(commandGateway).sendAndWait(any(CreatePolicyDirectlyCommand.class));
        verify(billingServicePort).generatePremiumSchedule(any());
    }

    @Test
    void oneStepPricingFailureStopsBeforePolicyAndBillingCreation() {
        when(confirmedPremiumPricingPort.confirm(any())).thenThrow(new IllegalStateException("rate missing"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> orchestrator.orchestrate(IssuanceProcessConfig.oneStep(PRODUCT_ID), request()));

        assertEquals(PolicyErrorCode.ISSUANCE_PREMIUM_CONFIRMATION_FAILED.getCode(), exception.getErrorCode());
        verifyNoInteractions(commandGateway, premiumCollectionOrchestrator);
    }

    private IssuanceRequest request() {
        InsuredPartyList.HolderInfo holder = new InsuredPartyList.HolderInfo("CUSTOMER_HOLDER", "HOLDER_001",
                "张三", null, null, null);
        InsuredPartyList.InsuredInfo insured = new InsuredPartyList.InsuredInfo("CUSTOMER_INSURED", "INSURED_001",
                "李四", null, null, 35, CustomerGender.MALE, null, null);
        InsuredPartyList parties = new InsuredPartyList("PARTIES_001", holder, List.of(insured), List.of());
        IssuancePlanLine main = new IssuancePlanLine(1, PRODUCT_ID, ProductCategory.MAIN, null,
                Money.of(new BigDecimal("500000"), "CNY"), null, null, PaymentFrequency.ANNUAL, 20, List.of(),
                null);
        return new IssuanceRequest(BIZ_NO, TENANT_ID, "USER_001", "PACKAGE_001", IssuanceStrategy.MERGE_ONE_POLICY,
                "CUSTOMER_HOLDER", parties, PolicyForm.INDIVIDUAL, null, LocalDateTime.now(),
                LocalDateTime.now().plusYears(1), PremiumCollectionMode.ONLINE, "CHANNEL_001", SalesChannel.ONLINE,
                "AGENT_001", List.of(main), Money.of(new BigDecimal("1000"), "CNY"), null);
    }

    private IssuanceRequest requestWithRider() {
        IssuanceRequest singleLine = request();
        IssuancePlanLine rider = new IssuancePlanLine(2, PRODUCT_ID, ProductCategory.RIDER, 1,
                Money.of(new BigDecimal("100000"), "CNY"), null, null, PaymentFrequency.ANNUAL, 1, List.of(), null);
        return new IssuanceRequest(singleLine.bizNo(), singleLine.tenantId(), singleLine.userId(),
                singleLine.marketPackageId(), singleLine.issuanceStrategy(), singleLine.holderCustomerId(),
                singleLine.insuredPartyList(), singleLine.policyForm(), singleLine.insuranceType(),
                singleLine.periodStart(), singleLine.periodEnd(), singleLine.collectionMode(), singleLine.channelId(),
                singleLine.salesChannel(), singleLine.agentId(), List.of(singleLine.mainLine(), rider),
                singleLine.quotedPremium(), singleLine.extendData());
    }

    private ProductIssueRules issueRules() {
        return new ProductIssueRules(0, 65, null, null, null, null, List.of(), List.of(), List.of(), 30, 15, null,
                false, List.of(), List.of(), null, List.of(), null, null, false, false);
    }
}
