package com.titanium.policy.aggregate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.titanium.metadata.enums.pricing.ChargeDirection;
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
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceAmountFact;
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

    /**
     * 无保费计划快照 + 趸缴：就地确立缴清计划（g04-05 减额缴清的「停缴」语义载体）。
     * <p>
     * 出单链路不组装保费计划快照（{@code premiumPlan} 恒 null）⇒ 若维持原判空拒绝，
     * 「减额缴清」这条链在任何真实保单上都会必然失败。而趸缴的领域含义即「一次性缴清、
     * 此后不再缴费」——应缴为零/方式趸缴/状态已缴无需从既有计划继承。
     * </p>
     */
    @Test
    void shouldMaterializePaidUpPlanWhenPolicyHasNoPremiumPlanSnapshot() {
        fixture.given(createdEventWithoutPremiumPlan(), activatedEvent())
                .when(paymentMethodCommand("SINGLE_PAYMENT"))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(
                                org.axonframework.test.matchers.Matchers.predicate(payload -> {
                                    PolicyMaintenanceAppliedEvent event = (PolicyMaintenanceAppliedEvent) payload;
                                    PremiumPlan plan = event.executionStateAfter().premiumPlan();
                                    assertEquals(PremiumPaymentMethod.SINGLE_PAYMENT, plan.paymentMethod());
                                    assertEquals(0, BigDecimal.ZERO.compareTo(plan.premiumAmount().value()));
                                    // 币种取自主险段合同快照（保额优先），非凭空指定
                                    assertEquals("CNY", plan.premiumAmount().currency());
                                    assertEquals(PremiumPaymentStatus.PAID, plan.paymentStatus());
                                    assertNull(plan.paymentCycle());
                                    assertNull(plan.premiumDueDate());
                                    return true;
                                }))))
                .expectState(policy -> {
                    assertEquals(PremiumPaymentMethod.SINGLE_PAYMENT,
                            policy.getPremiumPlan().paymentMethod());
                    assertEquals(PremiumPaymentStatus.PAID, policy.getPremiumPlan().paymentStatus());
                });
    }

    /**
     * 无保费计划快照 + 期缴：仍失败关闭。
     * <p>
     * 期缴的保费金额、缴费周期、到期日缺一即无从确定，任何默认值都是编造合同要素——此处<b>不得</b>
     * 与趸缴同样物化，否则等于凭空生成一份缴费计划。
     * </p>
     */
    @Test
    void shouldStillRejectInstallmentWhenPolicyHasNoPremiumPlanSnapshot() {
        fixture.given(createdEventWithoutPremiumPlan(), activatedEvent())
                .when(paymentMethodCommand("INSTALLMENT_PAYMENT"))
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

    /**
     * 纯金额类保全（追加/减额缴费、追加投资、部分领取）无字段变更、无合同状态动作，
     * 其变更实质是一笔已登记的收付差额——必须作为并列载荷写入批单事件，否则案件在保单域被误判为空交易。
     */
    @Test
    void shouldApplyAmountOnlyMaintenanceAndCarryRecordedDifference() {
        ApplyPolicyMaintenanceCommand command = amountCommand("ADDITIONAL_PAYMENT", "1000.00");

        fixture.given(createdEvent(), activatedEvent())
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(
                                org.axonframework.test.matchers.Matchers.predicate(payload -> {
                                    PolicyMaintenanceAppliedEvent event = (PolicyMaintenanceAppliedEvent) payload;
                                    assertNotNull(event.amountFact(), "金额型案件必须把差额事实写入批单事件");
                                    assertEquals("ADDITIONAL_PAYMENT", event.amountFact().itemCode());
                                    assertEquals(0, event.amountFact().amount()
                                            .compareTo(new BigDecimal("1000.00")));
                                    assertEquals(ChargeDirection.DEBIT, event.amountFact().direction());
                                    assertEquals("CNY", event.amountFact().currency());
                                    assertEquals("POSTING-1", event.amountFact().sourceRef());
                                    assertEquals(PolicyDataUpdateType.ADDITIONAL_PAYMENT, event.updateType(),
                                            "批改类型由保全项推导，不得退化为兜底类型");
                                    return true;
                                }))));
    }

    /**
     * 金额项未登记批改类型映射时回落「保单信息变更」——这是<b>有意</b>的兜底而非遗漏：
     * 自造一个批次类型会让下游按未知类型处置，而金额列自带 item_code 可供对账。
     */
    @Test
    void shouldFallBackToPolicyInfoUpdateTypeForUnmappedAmountItem() {
        ApplyPolicyMaintenanceCommand command = amountCommand("TOP_UP", "500.00");

        fixture.given(createdEvent(), activatedEvent())
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(
                                org.axonframework.test.matchers.Matchers.predicate(payload -> {
                                    PolicyMaintenanceAppliedEvent event = (PolicyMaintenanceAppliedEvent) payload;
                                    assertEquals(PolicyDataUpdateType.POLICY_INFO_CHANGE, event.updateType());
                                    assertEquals("TOP_UP", event.amountFact().itemCode(),
                                            "类型可兜底，保全项编码不得丢——它是事后对账的唯一线索");
                                    return true;
                                }))));
    }

    /** 差额参与请求摘要：同一 requestId 换一笔金额即不同载荷，必须拒绝而非静默幂等返回旧批单。 */
    @Test
    void shouldRejectSameRequestIdWithDifferentAmountDifference() {
        ApplyPolicyMaintenanceCommand applied = amountCommand("ADDITIONAL_PAYMENT", "1000.00");

        fixture.given(createdEvent(), activatedEvent(), appliedEvent(applied))
                .when(amountCommand("ADDITIONAL_PAYMENT", "2000.00"))
                .expectException(PolicyBusinessRuleException.class)
                .expectNoEvents();
    }

    /**
     * 🔴 摘要文案相同、仅收付金额不同，仍必须判为载荷冲突。
     * <p>
     * 上一条用例（差额同时体现在文案与结构化载荷里）会被文案那条路径顺带满足，<b>证明不了金额进了摘要</b>——
     * 已用反向注入实测：把金额从摘要计算中剔除后该用例仍全绿。金额是<b>结构化载荷</b>的组成部分，
     * 其权威性不能寄望于展示文案恰好把它带上（文案格式可变、可被调用方定制）。
     * </p>
     */
    @Test
    void shouldRejectSameRequestIdWithIdenticalSummaryButDifferentAmount() {
        ApplyPolicyMaintenanceCommand applied = amountCommand("ADDITIONAL_PAYMENT", "1000.00");
        ApplyPolicyMaintenanceCommand conflicting =
                amountCommand("ADDITIONAL_PAYMENT", "2000.00", applied.changeSummary());

        fixture.given(createdEvent(), activatedEvent(), appliedEvent(applied))
                .when(conflicting)
                .expectException(PolicyBusinessRuleException.class)
                .expectNoEvents();
    }

    /** 三种载荷（字段变更 / 金额事实 / 合同状态动作）全空 = 空交易，写入一条「什么都没发生」的批单不可接受。 */
    @Test
    void shouldRejectMaintenanceWithoutAnyEffectPayload() {
        fixture.given(createdEvent(), activatedEvent())
                .when(emptyPayloadCommand())
                .expectException(PolicyBusinessRuleException.class)
                .expectNoEvents();
    }

    // 🔴 NEXT_PERIOD（次期生效）与 POLICY_ANNIVERSARY（保单周年日）在保全域同义（同一快照字段、同一日期、
    //    同一调度路径），保单侧必须同等放行：漏登会让该方式在建案与调度全部成功之后，于生效回写这一步被
    //    POLICY_MAINTENANCE_EFFECTIVE_TIME_UNSUPPORTED 拒绝。删除白名单中的任一项本参数化用例即红。
    @ParameterizedTest
    @ValueSource(strings = {
            "FUTURE", "SPECIFIED_DATE", "NEXT_BILLING_DATE", "POLICY_ANNIVERSARY", "NEXT_PERIOD"})
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

    /**
     * 🔴 G11/g11-04 ⑩ / AC-05 正例：多主险段（交强 + 商业）**各自**齐备（productId/productVersion/
     * pricingPlanVersion）时保全放行；保单级保额按 D3 取**各主险段之和**（拒不「取首段」）。
     */
    @Test
    void shouldApplyMainCoverageWhenPolicyHasMultipleCompleteMainLines() {
        fixture.given(createdEventWithMainProducts(List.of(
                        mainProduct("line-1", 1, "product-1", "100000"),
                        mainProduct("line-2", 2, "product-2", "50000"))),
                activatedEvent())
                .when(coverageCommand("120000"))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(
                                org.axonframework.test.matchers.Matchers.predicate(payload -> {
                                    PolicyMaintenanceAppliedEvent event = (PolicyMaintenanceAppliedEvent) payload;
                                    assertEquals(2, event.executionStateAfter().policyProducts().size());
                                    return true;
                                }))))
                .expectState(policy -> assertEquals(new BigDecimal("170000.00"),
                        policy.getSumInsured().value(), "多主险保单级保额 = 各主险段之和（120000 + 50000）"));
    }

    /**
     * 🔴 G11/g11-04 ⑩ / AC-05 反-3：**任一**主险段缺 pricingPlanVersion 即拒——单主险时该判据与旧
     * 行为等价（原「size==1 且三要素齐备」），多主险时逐段校验、不得只看首个主险段。
     */
    @Test
    void shouldRejectMainCoverageWhenAnyMainLineLacksPricingPlanVersion() {
        fixture.given(createdEventWithMainProducts(List.of(
                        mainProduct("line-1", 1, "product-1", "100000"),
                        mainProduct("line-2", 2, "product-2", "50000", null))),
                activatedEvent())
                .when(coverageCommand("120000"))
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

    /** 金额型保全命令：无字段变更、无状态动作，变更实质是一笔已登记的收付差额。 */
    private ApplyPolicyMaintenanceCommand amountCommand(String itemCode, String amount) {
        String summary = "maintenance=maintenance-1;items=" + itemCode
                + ";fields=;action=NONE;amount=DEBIT:" + amount + ":CNY";
        return amountCommand(itemCode, amount, summary);
    }

    /** 摘要文案由调用方给定：用于构造「文案相同、结构化载荷不同」这类只有摘要计算能区分的场景。 */
    private ApplyPolicyMaintenanceCommand amountCommand(String itemCode, String amount, String summary) {
        String requestId = REQUEST_ID + "-amount";
        PolicyMaintenanceAmountFact amountFact = new PolicyMaintenanceAmountFact(
                itemCode, new BigDecimal(amount), ChargeDirection.DEBIT, "CNY", "POSTING-1");
        String hash = PolicyMaintenanceHashing.requestHash(
                TENANT_ID, POLICY_ID, requestId, "maintenance-1", 0, "a".repeat(64),
                "IMMEDIATE", EFFECTIVE_AT, summary, List.of(), amountFact, null,
                PolicyMaintenanceAction.NONE, null, null, null);
        return new ApplyPolicyMaintenanceCommand(
                POLICY_ID, requestId, "maintenance-1", 0, hash, "a".repeat(64),
                "IMMEDIATE", EFFECTIVE_AT, summary, List.of(), amountFact, null,
                PolicyMaintenanceAction.NONE, null, null, null, "operator-1", TENANT_ID);
    }

    /** 三种载荷全空的空交易命令：用于验证闸口拒绝，而非被某个分支偶然放行。 */
    private ApplyPolicyMaintenanceCommand emptyPayloadCommand() {
        String requestId = REQUEST_ID + "-empty";
        String summary = "maintenance=maintenance-1;items=;fields=;action=NONE";
        String hash = PolicyMaintenanceHashing.requestHash(
                TENANT_ID, POLICY_ID, requestId, "maintenance-1", 0, "a".repeat(64),
                "IMMEDIATE", EFFECTIVE_AT, summary, List.of());
        return new ApplyPolicyMaintenanceCommand(
                POLICY_ID, requestId, "maintenance-1", 0, hash, "a".repeat(64),
                "IMMEDIATE", EFFECTIVE_AT, summary, List.of(), "operator-1", TENANT_ID);
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
                null, null, PolicyMaintenanceAction.NONE, null, null, evidence);
        return new ApplyPolicyMaintenanceCommand(
                POLICY_ID, REQUEST_ID + "-retroactive", "maintenance-1", 0, hash,
                "a".repeat(64), "RETROACTIVE", EFFECTIVE_AT.minusMonths(1),
                "maintenance=maintenance-1;fields=policy.holder.mobile", changes,
                null, null, PolicyMaintenanceAction.NONE, null, null, evidence, "operator-1", TENANT_ID);
    }

    private PolicyMaintenanceAppliedEvent appliedEvent(ApplyPolicyMaintenanceCommand command) {
        InsuredPartyList updated = parties("13900000000");
        List<PolicyMaintenanceAppliedField> fields = List.of(new PolicyMaintenanceAppliedField(
                "POLICY_INFO_CHANGE", POLICY_ID, "policy.holder.mobile", "TEXT", "13900000000"));
        // 🔴 requestId 必须取自命令而非写死：写死会让「同 requestId 不同载荷」的用例根本走不到幂等分支
        //    （findMaintenanceApplication 查不到回执 ⇒ 落到下游守卫上），判据被兜底路径接管而失去区分力。
        String endorsementNo = PolicyMaintenanceHashing.stableEndorsementNo(
                TENANT_ID, POLICY_ID, command.requestId());
        return new PolicyMaintenanceAppliedEvent(
                POLICY_ID, command.requestId(), command.requestPayloadHash(), "maintenance-1", endorsementNo,
                com.titanium.policy.common.enums.PolicyDataUpdateType.POLICY_INFO_CHANGE,
                com.titanium.policy.common.enums.PolicyDataUpdateType.POLICY_INFO_CHANGE.getCategory(),
                0, 1, EFFECTIVE_AT, command.changeSummary(), null, null, "a".repeat(64), "b".repeat(64),
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
        return createdEvent(partyList, premiumPlan());
    }

    /**
     * 无保费计划快照的出单事件：生产链路 {@code CreatePolicyCommand} 两个发送点均传 {@code premiumPlan=null}
     * （实测 93 张保单 {@code payment_method} 全空），此为真实常态而非畸形数据。
     */
    private PolicyCreatedEvent createdEventWithoutPremiumPlan() {
        return createdEvent(parties("13800000000"), null);
    }

    private PolicyCreatedEvent createdEvent(InsuredPartyList partyList, PremiumPlan premiumPlan) {
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
                PolicyPeriod.of(EFFECTIVE_AT.minusYears(1), EFFECTIVE_AT.plusYears(10)),
                amount, amount, amount, List.of(mainProduct), premiumPlan, null, null, status,
                partyList, null, TENANT_ID);
    }

    private PolicyActivatedEvent activatedEvent() {
        return new PolicyActivatedEvent(POLICY_ID, EFFECTIVE_AT.minusDays(1), TENANT_ID);
    }

    /** 多主险段出单事件：G11/g11-04 ⑩ 多主险保全用例的基座。 */
    private PolicyCreatedEvent createdEventWithMainProducts(List<PolicyProduct> mainProducts) {
        Money amount = Money.of(new BigDecimal("1000.00"), "CNY");
        PolicyStatus status = new PolicyStatus(
                PolicyStatusCode.NOT_EFFECTIVE, EFFECTIVE_AT.minusDays(1), "创建", "system");
        return new PolicyCreatedEvent(
                POLICY_ID, new PolicyNo("P202608250001"), PolicyForm.INDIVIDUAL, "product-1",
                null, null, null, null, null,
                PolicyPeriod.of(EFFECTIVE_AT.minusYears(1), EFFECTIVE_AT.plusYears(10)),
                amount, amount, amount, mainProducts, premiumPlan(), null, null, status,
                parties("13800000000"), null, TENANT_ID);
    }

    /** 测试主险段：产品/版本/定价计划三要素齐备（G11/g11-04 ⑩ 多主险保全基座）。 */
    private PolicyProduct mainProduct(String lineId, int lineNo, String productId, String sumInsured) {
        return mainProduct(lineId, lineNo, productId, sumInsured, "plan-v8");
    }

    private PolicyProduct mainProduct(String lineId, int lineNo, String productId, String sumInsured,
                                      String pricingPlanVersion) {
        Money amount = Money.of(new BigDecimal(sumInsured), "CNY");
        return new PolicyProduct(lineId, lineNo, ProductCategory.MAIN, null, productId, "P00" + lineNo,
                "测试主险", "product-v3", pricingPlanVersion, null, amount, amount, null, null, null, null,
                List.of(), List.of(), List.of());
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
