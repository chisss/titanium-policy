package com.titanium.policy.aggregate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.titanium.metadata.enums.customer.CustomerEnum.CustomerGender;
import com.titanium.metadata.enums.customer.CustomerEnum.IdCardType;
import com.titanium.metadata.enums.maintenance.PolicyMaintenanceAction;
import com.titanium.metadata.enums.policy.BeneficiaryType;
import com.titanium.metadata.enums.policy.PolicyEnum.TerminationReason;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.command.ApplyPolicyMaintenanceCommand;
import com.titanium.policy.common.enums.FamilyRelation;
import com.titanium.policy.common.enums.PolicyDataUpdateType;
import com.titanium.policy.common.enums.PolicyStatusCode;
import com.titanium.policy.common.enums.PremiumPaymentCycle;
import com.titanium.policy.common.enums.PremiumPaymentMethod;
import com.titanium.policy.common.enums.PremiumPaymentStatus;
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
import com.titanium.policy.service.maintenance.HolderPolicyMaintenanceFieldExecutor;
import com.titanium.policy.service.maintenance.InsuredPolicyMaintenanceFieldExecutor;
import com.titanium.policy.service.maintenance.PaymentMethodPolicyMaintenanceFieldExecutor;
import com.titanium.policy.service.maintenance.PolicyMaintenanceFieldExecutorRegistry;
import com.titanium.policy.service.maintenance.PolicyMaintenanceHashing;
import com.titanium.policy.valueobject.PremiumPlan;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceApplicationReceipt;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceAppliedField;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceExecutionState;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceFieldChange;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceRetroactiveEvidence;
import com.titanium.policy.valueobject.policy.PolicyNo;
import com.titanium.policy.valueobject.policy.PolicyPeriod;
import com.titanium.policy.valueobject.policy.PolicyStatus;

class PolicyMaintenanceApplicationTest {

    private static final String POLICY_ID = "policy-1";
    private static final String TENANT_ID = "tenant-1";
    private static final String REQUEST_ID = "effect-request-1";
    /** 首位被保险人的聚合内标识：集合字段保全的对象标识（字段目录 identityField=insuredId） */
    private static final String INSURED_ID = "insured-1";
    private static final LocalDateTime EFFECTIVE_AT = LocalDateTime.of(2026, 8, 25, 10, 0);

    private FixtureConfiguration<Policy> fixture;
    private PolicyMaintenanceFieldExecutorRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new PolicyMaintenanceFieldExecutorRegistry(
                List.of(new HolderMobilePolicyMaintenanceFieldExecutor(),
                        new HolderPolicyMaintenanceFieldExecutor(),
                        new CoverageSumInsuredPolicyMaintenanceFieldExecutor(),
                        new BeneficiaryPolicyMaintenanceFieldExecutor(),
                        new InsuredPolicyMaintenanceFieldExecutor(),
                        new PaymentMethodPolicyMaintenanceFieldExecutor()));
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
    void shouldApplyPaymentMethodIntoPolicyPremiumPlan() {
        ApplyPolicyMaintenanceCommand command = paymentMethodCommand("SINGLE_PAYMENT");

        fixture.given(createdEvent(), activatedEvent())
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(
                                org.axonframework.test.matchers.Matchers.predicate(payload -> {
                                    PolicyMaintenanceAppliedEvent event = (PolicyMaintenanceAppliedEvent) payload;
                                    assertEquals(
                                            com.titanium.policy.common.enums.PolicyDataUpdateType.PAYMENT_METHOD_CHANGE,
                                            event.updateType());
                                    assertEquals("SINGLE_PAYMENT",
                                            event.appliedFields().getFirst().canonicalValue());
                                    assertEquals(PremiumPaymentMethod.SINGLE_PAYMENT,
                                            event.executionStateAfter().premiumPlan().paymentMethod());
                                    return true;
                                }))))
                .expectState(policy -> {
                    assertEquals(PremiumPaymentMethod.SINGLE_PAYMENT,
                            policy.getPremiumPlan().paymentMethod());
                    // 缴费方式属字段级变更：金额/周期/到期日/缴费状态一律保留，不触发计划重算
                    assertEquals(PremiumPaymentCycle.MONTHLY, policy.getPremiumPlan().paymentCycle());
                    assertEquals(new BigDecimal("1000.00"),
                            policy.getPremiumPlan().premiumAmount().value());
                });
    }

    @Test
    void shouldRejectUnknownPaymentMethodWithoutEvent() {
        fixture.given(createdEvent(), activatedEvent())
                .when(paymentMethodCommand("NOT_A_METHOD"))
                .expectException(PolicyBusinessRuleException.class)
                .expectNoEvents();
    }

    @Test
    void shouldApplyHolderIdentityFieldsIntoContractSnapshot() {
        ApplyPolicyMaintenanceCommand command = holderCommand(
                holderChange("policy.holder.name", "TEXT", "李四"),
                holderChange("policy.holder.gender", "ENUM", "FEMALE"),
                holderChange("policy.holder.birthDate", "DATE", "1990-05-20"),
                holderChange("policy.holder.documentType", "ENUM", "CHINA_ID_CARD"),
                holderChange("policy.holder.documentNumber", "TEXT", "ID-9"));

        fixture.given(createdEvent(), activatedEvent())
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(
                                org.axonframework.test.matchers.Matchers.predicate(payload -> {
                                    PolicyMaintenanceAppliedEvent event =
                                            (PolicyMaintenanceAppliedEvent) payload;
                                    assertEquals(PolicyDataUpdateType.HOLDER_CHANGE, event.updateType());
                                    InsuredPartyList.HolderInfo holder =
                                            event.executionStateAfter().insuredPartyList().holderInfo();
                                    assertEquals("李四", holder.name());
                                    assertEquals(CustomerGender.FEMALE, holder.gender());
                                    assertEquals(LocalDate.of(1990, 5, 20), holder.birthDate());
                                    assertEquals(IdCardType.CHINA_ID_CARD, holder.certType());
                                    assertEquals("ID-9", holder.certNo());
                                    // 联系方式属 POLICY_INFO_CHANGE（另一执行器），不得被身份要素批改波及
                                    assertEquals("13800000000", holder.phone());
                                    // 规范化回执口径：ENUM 取 code、DATE 取 ISO-8601，与字段目录发布类型逐字对应
                                    assertEquals(
                                            List.of("李四", "FEMALE", "1990-05-20", "CHINA_ID_CARD", "ID-9"),
                                            event.appliedFields().stream()
                                                    .map(PolicyMaintenanceAppliedField::canonicalValue)
                                                    .toList());
                                    return true;
                                }))))
                .expectState(policy -> {
                    InsuredPartyList.HolderInfo holder = policy.getInsuredPartyList().holderInfo();
                    assertEquals(CustomerGender.FEMALE, holder.gender());
                    assertEquals(LocalDate.of(1990, 5, 20), holder.birthDate());
                    assertEquals(IdCardType.CHINA_ID_CARD, holder.certType());
                    assertEquals("ID-9", holder.certNo());
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"M", "1", "男"})
    void shouldRejectHolderGenderOutsideCustomerEnumWithoutEvent(String gender) {
        fixture.given(createdEvent(), activatedEvent())
                .when(holderCommand(holderChange("policy.holder.gender", "ENUM", gender)))
                .expectException(PolicyBusinessRuleException.class)
                .expectNoEvents();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1990/05/20", "not-a-date", ""})
    void shouldRejectHolderBirthDateNotIsoDateWithoutEvent(String birthDate) {
        fixture.given(createdEvent(), activatedEvent())
                .when(holderCommand(holderChange("policy.holder.birthDate", "DATE", birthDate)))
                .expectException(PolicyBusinessRuleException.class)
                .expectNoEvents();
    }

    @Test
    void shouldRejectFutureHolderBirthDateWithoutEvent() {
        fixture.given(createdEvent(), activatedEvent())
                .when(holderCommand(holderChange(
                        "policy.holder.birthDate", "DATE", LocalDate.now().plusDays(1).toString())))
                .expectException(PolicyBusinessRuleException.class)
                .expectNoEvents();
    }

    @Test
    void shouldRejectHolderDocumentTypeOutsideCustomerEnumWithoutEvent() {
        fixture.given(createdEvent(), activatedEvent())
                .when(holderCommand(holderChange(
                        "policy.holder.documentType", "ENUM", "NOT_A_DOCUMENT_TYPE")))
                .expectException(PolicyBusinessRuleException.class)
                .expectNoEvents();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void shouldRejectBlankHolderNameWithoutEvent(String name) {
        fixture.given(createdEvent(), activatedEvent())
                .when(holderCommand(holderChange("policy.holder.name", "TEXT", name)))
                .expectException(PolicyBusinessRuleException.class)
                .expectNoEvents();
    }

    @Test
    void shouldRejectHolderFieldWithMismatchedDataTypeWithoutEvent() {
        // 字段目录声明 policy.holder.gender 为 ENUM，按 TEXT 提交即数据类型不符
        fixture.given(createdEvent(), activatedEvent())
                .when(holderCommand(holderChange("policy.holder.gender", "TEXT", "FEMALE")))
                .expectException(PolicyBusinessRuleException.class)
                .expectNoEvents();
    }

    @Test
    void shouldApplyInsuredIdentityFieldsIntoContractSnapshot() {
        ApplyPolicyMaintenanceCommand command = insuredCommand(
                insuredChange(INSURED_ID, "policy.insured.name", "TEXT", "李小四"),
                insuredChange(INSURED_ID, "policy.insured.documentNumber", "TEXT", "ID-9"));

        fixture.given(insuredCreatedEvent(), activatedEvent())
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(
                                org.axonframework.test.matchers.Matchers.predicate(payload -> {
                                    PolicyMaintenanceAppliedEvent event =
                                            (PolicyMaintenanceAppliedEvent) payload;
                                    assertEquals(PolicyDataUpdateType.INSURED_INFO_CHANGE, event.updateType());
                                    List<InsuredPartyList.InsuredInfo> insureds =
                                            event.executionStateAfter().insuredPartyList().insuredList();
                                    assertEquals("李小四", insureds.get(0).name());
                                    assertEquals("ID-9", insureds.get(0).certNo());
                                    // 非目标要素（年龄/证件类型/关系）原样透传
                                    assertEquals(30, insureds.get(0).age());
                                    assertEquals(IdCardType.CHINA_ID_CARD, insureds.get(0).certType());
                                    assertEquals("SELF", insureds.get(0).relationToHolder());
                                    // 同集合的另一位被保险人不得被批改波及
                                    assertEquals("王五", insureds.get(1).name());
                                    assertEquals("ID-3", insureds.get(1).certNo());
                                    // 规范化回执口径：两项均为 TEXT 原文，与字段目录发布类型逐字对应
                                    assertEquals(
                                            List.of("李小四", "ID-9"),
                                            event.appliedFields().stream()
                                                    .map(PolicyMaintenanceAppliedField::canonicalValue)
                                                    .toList());
                                    return true;
                                }))))
                .expectState(policy -> {
                    List<InsuredPartyList.InsuredInfo> insureds =
                            policy.getInsuredPartyList().insuredList();
                    assertEquals("李小四", insureds.get(0).name());
                    assertEquals("ID-9", insureds.get(0).certNo());
                    assertEquals("王五", insureds.get(1).name());
                });
    }

    @Test
    void shouldRejectInsuredOutsidePartyListWithoutEvent() {
        // 目标被保险人不存在必须失败关闭：被保险人是承保标的，不得按此路径新建（增员走 AddInsuredMemberCommand）
        fixture.given(insuredCreatedEvent(), activatedEvent())
                .when(insuredCommand(
                        insuredChange("insured-9", "policy.insured.name", "TEXT", "赵六")))
                .expectException(PolicyBusinessRuleException.class)
                .expectNoEvents();
    }

    @ParameterizedTest
    @MethodSource("illegalInsuredObjectIds")
    void shouldRejectInsuredObjectIdNotInProjectionFormatWithoutEvent(String objectId) {
        // 受理快照发布的对象标识恒为 1-32 位 [A-Za-z0-9._-]；非该形态即拒，绝不退化为「按顺序改第一个」
        fixture.given(insuredCreatedEvent(), activatedEvent())
                .when(insuredCommand(insuredChange(objectId, "policy.insured.name", "TEXT", "李小四")))
                .expectException(PolicyBusinessRuleException.class)
                .expectNoEvents();
    }

    /** 非法集合对象标识：超长、含非法字符。 */
    private static Stream<String> illegalInsuredObjectIds() {
        return Stream.of("insured#1", "i".repeat(33));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void shouldRejectBlankInsuredNameWithoutEvent(String name) {
        fixture.given(insuredCreatedEvent(), activatedEvent())
                .when(insuredCommand(insuredChange(INSURED_ID, "policy.insured.name", "TEXT", name)))
                .expectException(PolicyBusinessRuleException.class)
                .expectNoEvents();
    }

    @Test
    void shouldRejectInsuredFieldWithMismatchedDataTypeWithoutEvent() {
        // 字段目录声明 policy.insured.name 为 TEXT，按 DECIMAL 提交即数据类型不符
        fixture.given(insuredCreatedEvent(), activatedEvent())
                .when(insuredCommand(insuredChange(INSURED_ID, "policy.insured.name", "DECIMAL", "1")))
                .expectException(PolicyBusinessRuleException.class)
                .expectNoEvents();
    }

    @Test
    void shouldPreservePremiumPlanWhenOtherFieldApplied() {
        fixture.given(createdEvent(), activatedEvent())
                .when(command("13900000000"))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(
                                org.axonframework.test.matchers.Matchers.predicate(payload -> {
                                    PolicyMaintenanceAppliedEvent event = (PolicyMaintenanceAppliedEvent) payload;
                                    assertNotNull(event.executionStateAfter().premiumPlan());
                                    assertEquals(PremiumPaymentMethod.INSTALLMENT_PAYMENT,
                                            event.executionStateAfter().premiumPlan().paymentMethod());
                                    return true;
                                }))))
                .expectState(policy -> assertNotNull(policy.getPremiumPlan()));
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

    /**
     * 投保人身份要素变更（HOLDER_CHANGE 类）：作用对象为保单本身，故 objectId 即保单ID。
     */
    private PolicyMaintenanceFieldChange holderChange(String fieldCode, String dataType, String value) {
        return new PolicyMaintenanceFieldChange("HOLDER_CHANGE", POLICY_ID, fieldCode, dataType, value);
    }

    private ApplyPolicyMaintenanceCommand holderCommand(PolicyMaintenanceFieldChange... changes) {
        String requestId = REQUEST_ID + "-holder";
        List<PolicyMaintenanceFieldChange> changeList = List.of(changes);
        String summary = "maintenance=maintenance-1;fields=policy.holder";
        String hash = PolicyMaintenanceHashing.requestHash(
                TENANT_ID, POLICY_ID, requestId, "maintenance-1", 0,
                "a".repeat(64), "IMMEDIATE", EFFECTIVE_AT, summary, changeList);
        return new ApplyPolicyMaintenanceCommand(
                POLICY_ID, requestId, "maintenance-1", 0, hash, "a".repeat(64),
                "IMMEDIATE", EFFECTIVE_AT, summary, changeList, "operator-1", TENANT_ID);
    }

    /**
     * 被保险人身份要素变更（INSURED_INFO_CHANGE 类）：集合字段，objectId 即集合元素在聚合内的 insuredId。
     */
    private PolicyMaintenanceFieldChange insuredChange(
            String objectId, String fieldCode, String dataType, String value) {
        return new PolicyMaintenanceFieldChange("INSURED_INFO_CHANGE", objectId, fieldCode, dataType, value);
    }

    private ApplyPolicyMaintenanceCommand insuredCommand(PolicyMaintenanceFieldChange... changes) {
        String requestId = REQUEST_ID + "-insured";
        List<PolicyMaintenanceFieldChange> changeList = List.of(changes);
        String summary = "maintenance=maintenance-1;fields=policy.insured";
        String hash = PolicyMaintenanceHashing.requestHash(
                TENANT_ID, POLICY_ID, requestId, "maintenance-1", 0,
                "a".repeat(64), "IMMEDIATE", EFFECTIVE_AT, summary, changeList);
        return new ApplyPolicyMaintenanceCommand(
                POLICY_ID, requestId, "maintenance-1", 0, hash, "a".repeat(64),
                "IMMEDIATE", EFFECTIVE_AT, summary, changeList, "operator-1", TENANT_ID);
    }

    private ApplyPolicyMaintenanceCommand paymentMethodCommand(String methodCode) {
        String requestId = REQUEST_ID + "-payment-method";
        List<PolicyMaintenanceFieldChange> changes = List.of(new PolicyMaintenanceFieldChange(
                "PAYMENT_METHOD_CHANGE", POLICY_ID, "policy.payment.method", "ENUM", methodCode));
        String summary = "maintenance=maintenance-1;fields=policy.payment.method";
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
        return createdEvent(parties("13800000000"));
    }

    /** 含两名被保险人的出单事件：集合字段保全需按 insuredId 定位到具体元素 */
    private PolicyCreatedEvent insuredCreatedEvent() {
        return createdEvent(insuredParties());
    }

    private PolicyCreatedEvent createdEvent(InsuredPartyList partyList) {
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
                amount, amount, amount, List.of(mainProduct), premiumPlan(), null, null, status,
                partyList, null, TENANT_ID);
    }

    private PolicyActivatedEvent activatedEvent() {
        return new PolicyActivatedEvent(POLICY_ID, EFFECTIVE_AT.minusDays(1), TENANT_ID);
    }

    private InsuredPartyList parties(String mobile) {
        InsuredPartyList.HolderInfo holder = new InsuredPartyList.HolderInfo(
                "customer-1", "holder-1", "张三", null, "ID-1", mobile);
        return new InsuredPartyList("parties-1", holder, List.of(), List.of());
    }

    /** 两名被保险人的参与方快照：用于验证集合字段按 insuredId 精确定位、不波及同集合其它元素 */
    private InsuredPartyList insuredParties() {
        InsuredPartyList.HolderInfo holder = new InsuredPartyList.HolderInfo(
                "customer-1", "holder-1", "张三", null, "ID-1", "13800000000");
        InsuredPartyList.InsuredInfo first = new InsuredPartyList.InsuredInfo(
                "customer-2", INSURED_ID, "李四", IdCardType.CHINA_ID_CARD, "ID-2", 30,
                CustomerGender.MALE, "13700000000", "SELF", FamilyRelation.SELF);
        InsuredPartyList.InsuredInfo second = new InsuredPartyList.InsuredInfo(
                "customer-3", "insured-2", "王五", IdCardType.CHINA_ID_CARD, "ID-3", 8,
                CustomerGender.MALE, null, "CHILD", FamilyRelation.CHILD);
        return new InsuredPartyList("parties-1", holder, List.of(first, second), List.of());
    }

    /** 期缴保费计划：缴费方式变更执行器的写入目标，也是「变更参与方不得丢计划」回归的观测点 */
    private PremiumPlan premiumPlan() {
        return new PremiumPlan(Money.of(new BigDecimal("1000.00"), "CNY"),
                PremiumPaymentMethod.INSTALLMENT_PAYMENT, PremiumPaymentCycle.MONTHLY,
                EFFECTIVE_AT.minusDays(1), PremiumPaymentStatus.UNPAID);
    }
}
