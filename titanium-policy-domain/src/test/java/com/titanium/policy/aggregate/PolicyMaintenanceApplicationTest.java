package com.titanium.policy.aggregate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.titanium.metadata.enums.maintenance.PolicyMaintenanceAction;
import com.titanium.metadata.enums.policy.BeneficiaryType;
import com.titanium.metadata.enums.policy.PolicyEnum.TerminationReason;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.command.ApplyPolicyMaintenanceCommand;
import com.titanium.policy.common.enums.PolicyStatusCode;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.entity.policy.PolicyProduct;
import com.titanium.policy.event.PolicyActivatedEvent;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.event.PolicyLapsedEvent;
import com.titanium.policy.event.PolicyMaintenanceAppliedEvent;
import com.titanium.policy.event.PolicyMaintenanceRetroactiveEvidenceRecordedEvent;
import com.titanium.policy.event.PolicyMaintenanceStateAppliedEvent;
import com.titanium.policy.event.PolicySuspendedEvent;
import com.titanium.policy.exception.PolicyBusinessRuleException;
import com.titanium.policy.service.maintenance.BeneficiaryPolicyMaintenanceFieldExecutor;
import com.titanium.policy.service.maintenance.CoverageSumInsuredPolicyMaintenanceFieldExecutor;
import com.titanium.policy.service.maintenance.HolderMobilePolicyMaintenanceFieldExecutor;
import com.titanium.policy.service.maintenance.PolicyMaintenanceFieldExecutorRegistry;
import com.titanium.policy.service.maintenance.PolicyMaintenanceHashing;
import com.titanium.policy.valueobject.PolicyNo;
import com.titanium.policy.valueobject.PolicyStatus;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceApplicationReceipt;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceAppliedField;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceExecutionState;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceFieldChange;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceRetroactiveEvidence;
import com.titanium.policy.valueobject.policy.PolicyPeriod;

class PolicyMaintenanceApplicationTest {

    private static final String POLICY_ID = "policy-1";
    private static final String TENANT_ID = "tenant-1";
    private static final String REQUEST_ID = "effect-request-1";
    private static final LocalDateTime EFFECTIVE_AT = LocalDateTime.of(2026, 8, 25, 10, 0);

    private FixtureConfiguration<Policy> fixture;
    private PolicyMaintenanceFieldExecutorRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new PolicyMaintenanceFieldExecutorRegistry(
                List.of(new HolderMobilePolicyMaintenanceFieldExecutor(),
                        new CoverageSumInsuredPolicyMaintenanceFieldExecutor(),
                        new BeneficiaryPolicyMaintenanceFieldExecutor()));
        fixture = new AggregateTestFixture<>(Policy.class);
        fixture.registerInjectableResource(registry);
        fixture.setReportIllegalStateChange(false);
    }

    @Test
    void shouldApplyHolderMobileAndReturnAuthoritativeReceipt() {
        ApplyPolicyMaintenanceCommand command = command("13900000000");

        fixture.given(createdEvent(), activatedEvent())
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectResultMessageMatching(org.axonframework.test.matchers.Matchers.predicate(message -> {
                    PolicyMaintenanceApplicationReceipt receipt =
                            (PolicyMaintenanceApplicationReceipt) message.getPayload();
                    return receipt.actualPolicyVersion() == 1
                            && "13900000000".equals(
                                    receipt.appliedFields().getFirst().canonicalValue());
                }))
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(
                                org.axonframework.test.matchers.Matchers.predicate(payload -> {
                                    PolicyMaintenanceAppliedEvent event =
                                            (PolicyMaintenanceAppliedEvent) payload;
                                    assertEquals(1, event.actualPolicyVersion());
                                    assertEquals("13900000000", event.executionStateAfter()
                                            .insuredPartyList().holderInfo().phone());
                                    assertTrue(event.appliedSnapshotContentHash().matches("[a-f0-9]{64}"));
                                    return true;
                                }))));
    }

    @Test
    void shouldApplyMainCoverageAmountAndReturnCoverageEndorsement() {
        ApplyPolicyMaintenanceCommand command = coverageCommand("120000");

        fixture.given(createdEvent(), activatedEvent())
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(
                                org.axonframework.test.matchers.Matchers.predicate(payload -> {
                                    PolicyMaintenanceAppliedEvent event = (PolicyMaintenanceAppliedEvent) payload;
                                    assertEquals(
                                            com.titanium.policy.common.enums.PolicyDataUpdateType.COVERAGE_AMOUNT_CHANGE,
                                            event.updateType());
                                    assertEquals("120000", event.appliedFields().getFirst().canonicalValue());
                                    assertEquals(new BigDecimal("120000.00"), event.executionStateAfter()
                                            .policyProducts().getFirst().sumInsured().value());
                                    assertTrue(!event.originalSnapshotHash()
                                            .equals(event.appliedSnapshotContentHash()));
                                    return true;
                                }))))
                .expectState(policy -> {
                    assertEquals(new BigDecimal("120000.00"), policy.getSumInsured().value());
                    assertEquals(new BigDecimal("120000.00"),
                            policy.getPolicyProducts().getFirst().sumInsured().value());
                });
    }

    @Test
    void shouldAddBeneficiaryAndReturnPartyEndorsement() {
        ApplyPolicyMaintenanceCommand command = beneficiaryCommand();

        fixture.given(createdEvent(), activatedEvent())
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(
                                org.axonframework.test.matchers.Matchers.predicate(payload -> {
                                    PolicyMaintenanceAppliedEvent event = (PolicyMaintenanceAppliedEvent) payload;
                                    assertEquals(
                                            com.titanium.policy.common.enums.PolicyDataUpdateType.BENEFICIARY_CHANGE,
                                            event.updateType());
                                    InsuredPartyList.BeneficiaryInfo beneficiary = event.executionStateAfter()
                                            .insuredPartyList().beneficiaryList().getFirst();
                                    assertEquals("beneficiary-20260828", beneficiary.beneficiaryId());
                                    assertEquals("李四", beneficiary.name());
                                    assertEquals(BeneficiaryType.DEATH, beneficiary.beneficiaryType());
                                    assertEquals(1d, beneficiary.beneficiaryRatio());
                                    assertEquals(3, event.appliedFields().size());
                                    return true;
                                }))));
    }

    @Test
    void shouldReturnSameReceiptForSameRequestWithoutNewEvent() {
        ApplyPolicyMaintenanceCommand command = command("13900000000");
        PolicyMaintenanceAppliedEvent applied = appliedEvent(command);

        fixture.given(createdEvent(), activatedEvent(), applied)
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectNoEvents()
                .expectResultMessageMatching(org.axonframework.test.matchers.Matchers.predicate(message ->
                        ((PolicyMaintenanceApplicationReceipt) message.getPayload())
                                .endorsementNo().equals(applied.endorsementNo())));
    }

    @Test
    void shouldRejectDifferentPayloadForExistingRequest() {
        ApplyPolicyMaintenanceCommand original = command("13900000000");

        fixture.given(createdEvent(), activatedEvent(), appliedEvent(original))
                .when(command("13700000000"))
                .expectException(PolicyBusinessRuleException.class)
                .expectNoEvents();
    }

    @ParameterizedTest
    @ValueSource(strings = {"FUTURE", "SPECIFIED_DATE", "NEXT_BILLING_DATE", "POLICY_ANNIVERSARY"})
    void shouldApplyScheduledMaintenanceAfterEffectiveTimeArrives(String effectiveTimeType) {
        ApplyPolicyMaintenanceCommand command = scheduledCommand(
                effectiveTimeType, LocalDateTime.now().minusMinutes(1));

        fixture.given(createdEvent(), activatedEvent())
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(
                                org.axonframework.test.matchers.Matchers.predicate(
                                        PolicyMaintenanceAppliedEvent.class::isInstance))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN"})
    void shouldRejectUnsupportedEffectiveTimeType(String effectiveTimeType) {
        fixture.given(createdEvent(), activatedEvent())
                .when(scheduledCommand(effectiveTimeType, LocalDateTime.now().minusMinutes(1)))
                .expectException(PolicyBusinessRuleException.class)
                .expectNoEvents();
    }

    @Test
    void shouldApplyRetroactiveMaintenanceAndRecordEvidenceSeparately() {
        ApplyPolicyMaintenanceCommand command = retroactiveCommand();

        fixture.given(createdEvent(), activatedEvent())
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectResultMessageMatching(org.axonframework.test.matchers.Matchers.predicate(message ->
                        ((PolicyMaintenanceApplicationReceipt) message.getPayload())
                                .retroactiveEvidence() != null))
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(
                                org.axonframework.test.matchers.Matchers.predicate(
                                        PolicyMaintenanceAppliedEvent.class::isInstance),
                                org.axonframework.test.matchers.Matchers.predicate(payload -> {
                                    PolicyMaintenanceRetroactiveEvidenceRecordedEvent event =
                                            (PolicyMaintenanceRetroactiveEvidenceRecordedEvent) payload;
                                    return event.evidence().billingResolutionId()
                                            .equals("billing-resolution-1");
                                }))));
    }

    @Test
    void shouldRejectRetroactiveMaintenanceWithoutEvidence() {
        fixture.given(createdEvent(), activatedEvent())
                .when(scheduledCommand("RETROACTIVE", LocalDateTime.now().minusMinutes(1)))
                .expectException(PolicyBusinessRuleException.class)
                .expectNoEvents();
    }

    @Test
    void shouldRejectImmediateMaintenanceBeforeEffectiveTimeArrives() {
        fixture.given(createdEvent(), activatedEvent())
                .when(scheduledCommand("IMMEDIATE", LocalDateTime.now().plusHours(1)))
                .expectException(PolicyBusinessRuleException.class)
                .expectNoEvents();
    }

    @Test
    void shouldSuspendPolicyWithUnifiedMaintenanceReceipt() {
        assertStateApplication(
                stateCommand(PolicyMaintenanceAction.SUSPEND),
                new Object[]{createdEvent(), activatedEvent()},
                PolicyStatusCode.EFFECTIVE,
                PolicyStatusCode.SUSPENDED);
    }

    @Test
    void shouldResumeSuspendedPolicyWithUnifiedMaintenanceReceipt() {
        assertStateApplication(
                stateCommand(PolicyMaintenanceAction.RESUME),
                new Object[]{createdEvent(), activatedEvent(),
                    new PolicySuspendedEvent(POLICY_ID, EFFECTIVE_AT.minusHours(1), TENANT_ID)},
                PolicyStatusCode.SUSPENDED,
                PolicyStatusCode.EFFECTIVE);
    }

    @Test
    void shouldReinstateLapsedPolicyWithUnifiedMaintenanceReceipt() {
        assertStateApplication(
                stateCommand(PolicyMaintenanceAction.REINSTATE),
                new Object[]{createdEvent(), activatedEvent(),
                    new PolicyLapsedEvent(POLICY_ID, "欠费失效", EFFECTIVE_AT.minusHours(1),
                            "billing", TENANT_ID)},
                PolicyStatusCode.LAPSED,
                PolicyStatusCode.EFFECTIVE);
    }

    @Test
    void shouldTerminatePolicyWithUnifiedMaintenanceReceipt() {
        assertStateApplication(
                stateCommand(PolicyMaintenanceAction.TERMINATE),
                new Object[]{createdEvent(), activatedEvent()},
                PolicyStatusCode.EFFECTIVE,
                PolicyStatusCode.TERMINATED);
    }

    @Test
    void shouldRejectStateActionWhenCurrentStatusDoesNotMatchWithoutEvent() {
        fixture.given(createdEvent(), activatedEvent())
                .when(stateCommand(PolicyMaintenanceAction.RESUME))
                .expectException(PolicyBusinessRuleException.class)
                .expectNoEvents();
    }

    private void assertStateApplication(
            ApplyPolicyMaintenanceCommand command,
            Object[] history,
            PolicyStatusCode statusBefore,
            PolicyStatusCode statusAfter) {
        fixture.given(history)
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectResultMessageMatching(org.axonframework.test.matchers.Matchers.predicate(message -> {
                    PolicyMaintenanceApplicationReceipt receipt =
                            (PolicyMaintenanceApplicationReceipt) message.getPayload();
                    return receipt.stateAction() == command.stateAction()
                            && receipt.statusBefore() == statusBefore
                            && receipt.statusAfter() == statusAfter
                            && receipt.appliedFields().isEmpty();
                }))
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(
                                org.axonframework.test.matchers.Matchers.predicate(payload -> {
                                    PolicyMaintenanceStateAppliedEvent event =
                                            (PolicyMaintenanceStateAppliedEvent) payload;
                                    return event.stateAction() == command.stateAction()
                                            && event.statusBefore() == statusBefore
                                            && event.statusAfter() == statusAfter;
                                }))));
    }

    private ApplyPolicyMaintenanceCommand command(String mobile) {
        return fieldCommand("IMMEDIATE", EFFECTIVE_AT, mobile);
    }

    private ApplyPolicyMaintenanceCommand scheduledCommand(
            String effectiveTimeType,
            LocalDateTime effectiveAt) {
        return fieldCommand(effectiveTimeType, effectiveAt, "13900000000");
    }

    private ApplyPolicyMaintenanceCommand fieldCommand(
            String effectiveTimeType,
            LocalDateTime effectiveAt,
            String mobile) {
        List<PolicyMaintenanceFieldChange> changes = List.of(new PolicyMaintenanceFieldChange(
                "POLICY_INFO_CHANGE", POLICY_ID, "policy.holder.mobile", "TEXT", mobile));
        String hash = PolicyMaintenanceHashing.requestHash(
                TENANT_ID, POLICY_ID, REQUEST_ID, "maintenance-1", 0,
                "a".repeat(64), effectiveTimeType, effectiveAt,
                "maintenance=maintenance-1;fields=policy.holder.mobile", changes);
        return new ApplyPolicyMaintenanceCommand(
                POLICY_ID, REQUEST_ID, "maintenance-1", 0, hash, "a".repeat(64),
                effectiveTimeType, effectiveAt,
                "maintenance=maintenance-1;fields=policy.holder.mobile", changes,
                "operator-1", TENANT_ID);
    }

    private ApplyPolicyMaintenanceCommand stateCommand(PolicyMaintenanceAction action) {
        String requestId = REQUEST_ID + "-" + action.name().toLowerCase();
        String reason = "maintenance=maintenance-1;action=" + action.name();
        TerminationReason terminationReason = action == PolicyMaintenanceAction.TERMINATE
                ? TerminationReason.WITHDRAWAL : null;
        String hash = PolicyMaintenanceHashing.requestHash(
                TENANT_ID, POLICY_ID, requestId, "maintenance-1", 0,
                "a".repeat(64), "IMMEDIATE", EFFECTIVE_AT, reason, List.of(),
                action, reason, terminationReason);
        return new ApplyPolicyMaintenanceCommand(
                POLICY_ID, requestId, "maintenance-1", 0, hash, "a".repeat(64),
                "IMMEDIATE", EFFECTIVE_AT, reason, List.of(), action, reason,
                terminationReason, "operator-1", TENANT_ID);
    }

    private ApplyPolicyMaintenanceCommand coverageCommand(String sumInsured) {
        String requestId = REQUEST_ID + "-coverage";
        List<PolicyMaintenanceFieldChange> changes = List.of(new PolicyMaintenanceFieldChange(
                "COVERAGE_AMOUNT_CHANGE", "line-1", "policy.coverage.sumInsured", "DECIMAL", sumInsured));
        String summary = "maintenance=maintenance-1;fields=policy.coverage.sumInsured";
        String hash = PolicyMaintenanceHashing.requestHash(
                TENANT_ID, POLICY_ID, requestId, "maintenance-1", 0,
                "a".repeat(64), "IMMEDIATE", EFFECTIVE_AT, summary, changes);
        return new ApplyPolicyMaintenanceCommand(
                POLICY_ID, requestId, "maintenance-1", 0, hash, "a".repeat(64),
                "IMMEDIATE", EFFECTIVE_AT, summary, changes, "operator-1", TENANT_ID);
    }

    private ApplyPolicyMaintenanceCommand beneficiaryCommand() {
        String requestId = REQUEST_ID + "-beneficiary";
        String objectId = "beneficiary-20260828";
        List<PolicyMaintenanceFieldChange> changes = List.of(
                new PolicyMaintenanceFieldChange(
                        "BENEFICIARY_CHANGE", objectId, "policy.beneficiary.name", "TEXT", "李四"),
                new PolicyMaintenanceFieldChange(
                        "BENEFICIARY_CHANGE", objectId, "policy.beneficiary.relationship", "ENUM", "DEATH"),
                new PolicyMaintenanceFieldChange(
                        "BENEFICIARY_CHANGE", objectId, "policy.beneficiary.share", "DECIMAL", "100"));
        String summary = "maintenance=maintenance-1;fields=policy.beneficiary";
        String hash = PolicyMaintenanceHashing.requestHash(
                TENANT_ID, POLICY_ID, requestId, "maintenance-1", 0,
                "a".repeat(64), "IMMEDIATE", EFFECTIVE_AT, summary, changes);
        return new ApplyPolicyMaintenanceCommand(
                POLICY_ID, requestId, "maintenance-1", 0, hash, "a".repeat(64),
                "IMMEDIATE", EFFECTIVE_AT, summary, changes, "operator-1", TENANT_ID);
    }

    private ApplyPolicyMaintenanceCommand retroactiveCommand() {
        List<PolicyMaintenanceFieldChange> changes = List.of(new PolicyMaintenanceFieldChange(
                "POLICY_INFO_CHANGE", POLICY_ID, "policy.holder.mobile", "TEXT", "13900000000"));
        PolicyMaintenanceRetroactiveEvidence evidence = new PolicyMaintenanceRetroactiveEvidence(
                "analysis-1", 1, "a".repeat(64), "period-recalculation-1", 1,
                "product-recalculation-1", "PERIOD_V1", "b".repeat(64), "c".repeat(64),
                "billing-batch-1", "d".repeat(64), "REVIEW_REQUIRED", "billing-resolution-1",
                "e".repeat(64), "2026-08", 1);
        String hash = PolicyMaintenanceHashing.requestHash(
                TENANT_ID, POLICY_ID, REQUEST_ID + "-retroactive", "maintenance-1", 0,
                "a".repeat(64), "RETROACTIVE", EFFECTIVE_AT.minusMonths(1),
                "maintenance=maintenance-1;fields=policy.holder.mobile", changes,
                PolicyMaintenanceAction.NONE, null, null, evidence);
        return new ApplyPolicyMaintenanceCommand(
                POLICY_ID, REQUEST_ID + "-retroactive", "maintenance-1", 0, hash,
                "a".repeat(64), "RETROACTIVE", EFFECTIVE_AT.minusMonths(1),
                "maintenance=maintenance-1;fields=policy.holder.mobile", changes,
                PolicyMaintenanceAction.NONE, null, null, evidence, "operator-1", TENANT_ID);
    }

    private PolicyMaintenanceAppliedEvent appliedEvent(ApplyPolicyMaintenanceCommand command) {
        InsuredPartyList updated = parties("13900000000");
        List<PolicyMaintenanceAppliedField> fields = List.of(new PolicyMaintenanceAppliedField(
                "POLICY_INFO_CHANGE", POLICY_ID, "policy.holder.mobile", "TEXT", "13900000000"));
        String endorsementNo = PolicyMaintenanceHashing.stableEndorsementNo(TENANT_ID, POLICY_ID, REQUEST_ID);
        return new PolicyMaintenanceAppliedEvent(
                POLICY_ID, REQUEST_ID, command.requestPayloadHash(), "maintenance-1", endorsementNo,
                com.titanium.policy.common.enums.PolicyDataUpdateType.POLICY_INFO_CHANGE,
                com.titanium.policy.common.enums.PolicyDataUpdateType.POLICY_INFO_CHANGE.getCategory(),
                0, 1, EFFECTIVE_AT, command.changeSummary(), "a".repeat(64), "b".repeat(64),
                "axon-event://policy/tenant-1/policy-1/maintenance-applications/effect-request-1?version=1",
                "c".repeat(64), "d".repeat(64), fields,
                new PolicyMaintenanceExecutionState(updated), EFFECTIVE_AT, "operator-1", TENANT_ID);
    }

    private PolicyCreatedEvent createdEvent() {
        Money amount = Money.of(new BigDecimal("1000.00"), "CNY");
        PolicyProduct mainProduct = new PolicyProduct(
                "line-1", 1, ProductCategory.MAIN, null, "product-1", "P001", "测试主险",
                "product-v3", "plan-v8", null, amount, amount, null, null, null, null,
                List.of(), List.of(), List.of());
        PolicyStatus status = new PolicyStatus(
                PolicyStatusCode.NOT_EFFECTIVE, EFFECTIVE_AT.minusDays(1), "创建", "system");
        return new PolicyCreatedEvent(
                POLICY_ID, new PolicyNo("P202608250001"), PolicyForm.INDIVIDUAL, "product-1",
                null, null, null, null, null,
                PolicyPeriod.of(EFFECTIVE_AT.minusYears(1), EFFECTIVE_AT.plusYears(10), 0, 0),
                amount, amount, amount, List.of(mainProduct), null, null, null, status,
                parties("13800000000"), null, TENANT_ID);
    }

    private PolicyActivatedEvent activatedEvent() {
        return new PolicyActivatedEvent(POLICY_ID, EFFECTIVE_AT.minusDays(1), TENANT_ID);
    }

    private InsuredPartyList parties(String mobile) {
        InsuredPartyList.HolderInfo holder = new InsuredPartyList.HolderInfo(
                "customer-1", "holder-1", "张三", null, "ID-1", mobile);
        return new InsuredPartyList("parties-1", holder, List.of(), List.of());
    }
}
