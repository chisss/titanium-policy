package com.titanium.policy.aggregate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.axonframework.test.matchers.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.maintenance.PolicyMaintenanceAction;
import com.titanium.metadata.enums.policy.PolicyEnum.TerminationReason;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.policy.PolicyServiceRestrictionAction;
import com.titanium.metadata.enums.policy.PolicyServiceRestrictionType;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.command.ApplyPolicyMaintenanceCommand;
import com.titanium.policy.command.TerminatePolicyCommand;
import com.titanium.policy.common.enums.PolicyDataUpdateType;
import com.titanium.policy.common.enums.PolicyStatusCode;
import com.titanium.policy.common.enums.PremiumPaymentCycle;
import com.titanium.policy.common.enums.PremiumPaymentMethod;
import com.titanium.policy.common.enums.PremiumPaymentStatus;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.entity.policy.PolicyProduct;
import com.titanium.policy.event.PolicyActivatedEvent;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.event.PolicyMaintenanceAppliedEvent;
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
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceExecutionState;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceFieldChange;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceServiceRestriction;
import com.titanium.policy.valueobject.policy.PolicyNo;
import com.titanium.policy.valueobject.policy.PolicyPeriod;
import com.titanium.policy.valueobject.policy.PolicyServiceRestrictions;
import com.titanium.policy.valueobject.policy.PolicyStatus;

/**
 * 服务受限标记在保单域写侧的权威准入测试（g04-09，AC-10 的保单侧落点）。
 * <p>
 * 保全域的前置准入（{@code MaintenanceServiceRestrictionAdmission}）只求「明显受限的案件不进入流程」，它依赖读模型
 * 快照（最终一致）；<b>权威拒绝必须在本聚合</b>——判据取聚合当前状态（强一致），投影滞后不会让受限保单被退保。
 * 本测试锁死三件事：
 * </p>
 * <ol>
 * <li><b>受限事实可生效且不改合同状态</b>——挂失保单仍是 {@code EFFECTIVE}，受限是与状态机正交的另一条维度；</li>
 * <li><b>受限态下终止被拒</b>（AC-10 反向判据的保单侧权威），且拒绝发生在版本/状态校验之前不留半提交；</li>
 * <li><b>受限可解除、解除后终止放行</b>——死锁守卫：若解除通道也走不通，受限将成为不可逆的永久状态。</li>
 * </ol>
 * <p>
 * 另覆盖两处易静默失效的闸口：受限载荷单独提交<b>不得</b>被判为空交易；受限载荷与字段/状态载荷<b>不得</b>混装。
 * </p>
 */
class PolicyServiceRestrictionAdmissionTest {

    private static final String        POLICY_ID  = "policy-1";
    private static final String        TENANT_ID  = "tenant-1";
    private static final LocalDateTime EFFECTIVE_AT = LocalDateTime.of(2026, 8, 25, 10, 0);

    private FixtureConfiguration<Policy> fixture;

    @BeforeEach
    void setUp() {
        PolicyMaintenanceFieldExecutorRegistry registry = new PolicyMaintenanceFieldExecutorRegistry(
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
    @DisplayName("AC-10①：受限事实生效后保单受限，且合同状态不变（挂失不等于中止）")
    void shouldApplyRestrictionWithoutChangingPolicyStatus() {
        ApplyPolicyMaintenanceCommand command = restrictionCommand(
                PolicyServiceRestrictionAction.APPLY, "effect-request-loss-report", 0);

        fixture.given(createdEvent(), activatedEvent())
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(Matchers.payloadsMatching(Matchers.exactSequenceOf(
                        Matchers.predicate(payload -> {
                            PolicyMaintenanceAppliedEvent event = (PolicyMaintenanceAppliedEvent) payload;
                            // 受限载荷单独提交是合法交易：若空交易闸口漏掉这一种载荷，此处即被误拒
                            assertTrue(event.serviceRestrictionsAfter() != null
                                            && event.serviceRestrictionsAfter().restricted(),
                                    "受限清单必须随批单事件落定，否则受限标记只存在于回执里、回放即丢");
                            assertTrue(event.appliedFields().isEmpty(),
                                    "挂失不改任何合同要素");
                            return true;
                        }))))
                .expectState(policy -> {
                    assertTrue(policy.serviceRestricted(), "回放后保单处于受限态");
                    assertEquals(PolicyStatusCode.EFFECTIVE, policy.getStatus().statusCode(),
                            "🔴 受限不改合同状态：挂失保单仍是生效保单");
                });
    }

    @Test
    @DisplayName("AC-10②：受限态下终止被拒，且不产生任何事件（权威拒绝在聚合，不看读模型）")
    void shouldRejectTerminationWhileServiceRestricted() {
        ApplyPolicyMaintenanceCommand restrict = restrictionCommand(
                PolicyServiceRestrictionAction.APPLY, "effect-request-loss-report", 0);

        fixture.given(createdEvent(), activatedEvent(), restrictionAppliedEvent(restrict))
                .when(stateCommand(PolicyMaintenanceAction.TERMINATE, "effect-request-terminate", 1))
                .expectException(PolicyBusinessRuleException.class)
                .expectNoEvents();
    }

    @Test
    @DisplayName("对照组：不受限时终止照常放行（证明上面的拒绝不是恒拒）")
    void shouldAdmitTerminationWhenNotRestricted() {
        fixture.given(createdEvent(), activatedEvent())
                .when(stateCommand(PolicyMaintenanceAction.TERMINATE, "effect-request-terminate", 0))
                .expectSuccessfulHandlerExecution();
    }

    @Test
    @DisplayName("🔴 死锁守卫：解除受限后终止放行（受限必须可解除，否则受限即终身）")
    void shouldAdmitTerminationAfterRestrictionReleased() {
        ApplyPolicyMaintenanceCommand restrict = restrictionCommand(
                PolicyServiceRestrictionAction.APPLY, "effect-request-loss-report", 0);
        ApplyPolicyMaintenanceCommand release = restrictionCommand(
                PolicyServiceRestrictionAction.RELEASE, "effect-request-loss-report-release", 1);
        PolicyServiceRestrictions restrictedState = PolicyServiceRestrictions.EMPTY
                .apply(restrict.serviceRestriction(), restrict.effectiveAt());

        fixture.given(createdEvent(), activatedEvent(),
                        restrictionAppliedEvent(restrict, PolicyServiceRestrictions.EMPTY),
                        restrictionAppliedEvent(release, restrictedState))
                .when(stateCommand(PolicyMaintenanceAction.TERMINATE, "effect-request-terminate", 2))
                .expectSuccessfulHandlerExecution()
                .expectState(policy -> assertEquals(PolicyStatusCode.TERMINATED,
                        policy.getStatus().statusCode(), "解除受限后退保生效"));
    }

    @Test
    @DisplayName("🔴 AC-10② 全入口一致：受限态下 web 端退保（WITHDRAWAL）同样被拒——闸门只守保全应用路径时该入口可绕过")
    void shouldRejectWithdrawalTerminationWhileServiceRestricted() {
        ApplyPolicyMaintenanceCommand restrict = restrictionCommand(
                PolicyServiceRestrictionAction.APPLY, "effect-request-loss-report", 0);

        fixture.given(createdEvent(), activatedEvent(), restrictionAppliedEvent(restrict))
                .when(new TerminatePolicyCommand(POLICY_ID, "退保", "operator-1",
                        TerminationReason.WITHDRAWAL, TENANT_ID))
                .expectException(PolicyBusinessRuleException.class)
                .expectNoEvents();
    }

    @Test
    @DisplayName("对照组：受限态下赔付后终止（FULL_PAYMENT）不得被挡——挂失不能阻断理赔给付")
    void shouldAdmitBenefitSettlementTerminationWhileServiceRestricted() {
        ApplyPolicyMaintenanceCommand restrict = restrictionCommand(
                PolicyServiceRestrictionAction.APPLY, "effect-request-loss-report", 0);

        fixture.given(createdEvent(), activatedEvent(), restrictionAppliedEvent(restrict))
                .when(new TerminatePolicyCommand(POLICY_ID, "身故给付终止", "operator-1",
                        TerminationReason.FULL_PAYMENT, TENANT_ID))
                .expectSuccessfulHandlerExecution()
                .expectState(policy -> assertEquals(PolicyStatusCode.TERMINATED,
                        policy.getStatus().statusCode(), "赔付后终止不受服务受限影响"));
    }

    @Test
    @DisplayName("受限载荷不得与字段变更或状态动作混装（保单一侧的对称闸口）")
    void shouldRejectRestrictionMixedWithOtherPayloads() {
        fixture.given(createdEvent(), activatedEvent())
                .when(mixedWithFieldCommand())
                .expectException(PolicyBusinessRuleException.class)
                .expectNoEvents();

        fixture.given(createdEvent(), activatedEvent())
                .when(mixedWithStateCommand())
                .expectException(PolicyBusinessRuleException.class)
                .expectNoEvents();
    }

    @Test
    @DisplayName("同一受限请求重放：返回既有回执且不重复施加（幂等由 requestId 承担）")
    void shouldReplaySameRestrictionRequestWithoutNewEvent() {
        ApplyPolicyMaintenanceCommand command = restrictionCommand(
                PolicyServiceRestrictionAction.APPLY, "effect-request-loss-report", 0);
        PolicyMaintenanceAppliedEvent applied = restrictionAppliedEvent(command);

        fixture.given(createdEvent(), activatedEvent(), applied)
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectNoEvents()
                .expectResultMessageMatching(Matchers.predicate(message ->
                        ((PolicyMaintenanceApplicationReceipt) message.getPayload())
                                .endorsementNo().equals(applied.endorsementNo())));
    }

    // ==================== 夹具 ====================

    /** 受限型保全命令：无字段、无金额、无状态动作，其变更实质就是这条受限事实。 */
    private ApplyPolicyMaintenanceCommand restrictionCommand(
            PolicyServiceRestrictionAction action,
            String requestId,
            long expectedVersion) {
        PolicyMaintenanceServiceRestriction restriction = restriction(
                PolicyServiceRestrictionType.LOSS_REPORT, action);
        String summary = summary(restriction);
        String hash = PolicyMaintenanceHashing.requestHash(
                TENANT_ID, POLICY_ID, requestId, "maintenance-1", expectedVersion,
                "a".repeat(64), "IMMEDIATE", EFFECTIVE_AT, summary, List.of(),
                null, restriction, PolicyMaintenanceAction.NONE, null, null, null);
        return new ApplyPolicyMaintenanceCommand(
                POLICY_ID, requestId, "maintenance-1", expectedVersion, hash, "a".repeat(64),
                "IMMEDIATE", EFFECTIVE_AT, summary, List.of(), null, restriction,
                PolicyMaintenanceAction.NONE, null, null, null, "operator-1", TENANT_ID);
    }

    private ApplyPolicyMaintenanceCommand stateCommand(
            PolicyMaintenanceAction action,
            String requestId,
            long expectedVersion) {
        String reason = "maintenance=maintenance-1;action=" + action.name();
        TerminationReason terminationReason = action == PolicyMaintenanceAction.TERMINATE
                ? TerminationReason.WITHDRAWAL : null;
        String hash = PolicyMaintenanceHashing.requestHash(
                TENANT_ID, POLICY_ID, requestId, "maintenance-1", expectedVersion,
                "a".repeat(64), "IMMEDIATE", EFFECTIVE_AT, reason, List.of(),
                action, reason, terminationReason);
        return new ApplyPolicyMaintenanceCommand(
                POLICY_ID, requestId, "maintenance-1", expectedVersion, hash, "a".repeat(64),
                "IMMEDIATE", EFFECTIVE_AT, reason, List.of(), action, reason,
                terminationReason, "operator-1", TENANT_ID);
    }

    /** 受限事实 + 字段变更：两种载荷语义不同，混提即无从判定本次变更的实质。 */
    private ApplyPolicyMaintenanceCommand mixedWithFieldCommand() {
        String requestId = "effect-request-mixed-field";
        PolicyMaintenanceServiceRestriction restriction = restriction(
                PolicyServiceRestrictionType.LOSS_REPORT, PolicyServiceRestrictionAction.APPLY);
        List<PolicyMaintenanceFieldChange> changes = List.of(new PolicyMaintenanceFieldChange(
                "POLICY_INFO_CHANGE", POLICY_ID, "policy.holder.mobile", "TEXT", "13900000000"));
        String summary = summary(restriction) + ";fields=policy.holder.mobile";
        String hash = PolicyMaintenanceHashing.requestHash(
                TENANT_ID, POLICY_ID, requestId, "maintenance-1", 0, "a".repeat(64),
                "IMMEDIATE", EFFECTIVE_AT, summary, changes,
                null, restriction, PolicyMaintenanceAction.NONE, null, null, null);
        return new ApplyPolicyMaintenanceCommand(
                POLICY_ID, requestId, "maintenance-1", 0, hash, "a".repeat(64),
                "IMMEDIATE", EFFECTIVE_AT, summary, changes, null, restriction,
                PolicyMaintenanceAction.NONE, null, null, null, "operator-1", TENANT_ID);
    }

    /** 受限事实 + 终止动作：挂失不改合同状态，两者混装即语义冲突。 */
    private ApplyPolicyMaintenanceCommand mixedWithStateCommand() {
        String requestId = "effect-request-mixed-state";
        PolicyMaintenanceServiceRestriction restriction = restriction(
                PolicyServiceRestrictionType.LOSS_REPORT, PolicyServiceRestrictionAction.APPLY);
        String reason = "maintenance=maintenance-1;action=TERMINATE";
        String summary = summary(restriction) + ";action=TERMINATE";
        String hash = PolicyMaintenanceHashing.requestHash(
                TENANT_ID, POLICY_ID, requestId, "maintenance-1", 0, "a".repeat(64),
                "IMMEDIATE", EFFECTIVE_AT, summary, List.of(), null, restriction,
                PolicyMaintenanceAction.TERMINATE, reason, TerminationReason.WITHDRAWAL, null);
        return new ApplyPolicyMaintenanceCommand(
                POLICY_ID, requestId, "maintenance-1", 0, hash, "a".repeat(64),
                "IMMEDIATE", EFFECTIVE_AT, summary, List.of(), null, restriction,
                PolicyMaintenanceAction.TERMINATE, reason, TerminationReason.WITHDRAWAL, null,
                "operator-1", TENANT_ID);
    }

    /** 受限型批单事件（起点为无受限）：载荷与命令逐字对齐，故幂等重放与版本推进都能对上。 */
    private PolicyMaintenanceAppliedEvent restrictionAppliedEvent(ApplyPolicyMaintenanceCommand command) {
        return restrictionAppliedEvent(command, PolicyServiceRestrictions.EMPTY);
    }

    /**
     * 受限型批单事件（显式指定前序清单）。
     * <p>
     * 事件的 {@code serviceRestrictionsAfter} 是<b>施加之后</b>的清单，回放时直接覆盖状态（非增量重算），
     * 故解除类事件的起点必须是前一次受限之后的清单——一律从 {@code EMPTY} 起算会被值对象的失败关闭拒绝
     * （对未受限的种类解除）。
     * </p>
     */
    private PolicyMaintenanceAppliedEvent restrictionAppliedEvent(
            ApplyPolicyMaintenanceCommand command,
            PolicyServiceRestrictions before) {
        PolicyServiceRestrictions restrictionsAfter = before
                .apply(command.serviceRestriction(), command.effectiveAt());
        return new PolicyMaintenanceAppliedEvent(
                POLICY_ID, command.requestId(), command.requestPayloadHash(), "maintenance-1",
                PolicyMaintenanceHashing.stableEndorsementNo(TENANT_ID, POLICY_ID, command.requestId()),
                PolicyDataUpdateType.POLICY_INFO_CHANGE,
                PolicyDataUpdateType.POLICY_INFO_CHANGE.getCategory(),
                command.expectedPolicyVersion(), command.expectedPolicyVersion() + 1,
                command.effectiveAt(), command.changeSummary(), null, restrictionsAfter,
                command.proposedSnapshotHash(), "b".repeat(64),
                "axon-event://policy/" + TENANT_ID + "/" + POLICY_ID
                        + "/maintenance-applications/" + command.requestId() + "?version=1",
                "c".repeat(64), "d".repeat(64), List.of(),
                new PolicyMaintenanceExecutionState(parties("13800000000")),
                command.effectiveAt(), "operator-1", TENANT_ID);
    }

    private PolicyCreatedEvent createdEvent() {
        Money amount = Money.of(new BigDecimal("1000.00"), "CNY");
        PolicyProduct mainProduct = new PolicyProduct(
                "line-1", 1, ProductCategory.MAIN, null, "product-1", "P001", "测试主险",
                "product-v3", "plan-v8", null, amount, amount, null, null, null, null,
                List.of(), List.of(), List.of());
        PolicyStatus status = new PolicyStatus(
                PolicyStatusCode.NOT_EFFECTIVE, EFFECTIVE_AT.minusDays(1), "创建", "system");
        PremiumPlan premiumPlan = new PremiumPlan(amount, PremiumPaymentMethod.INSTALLMENT_PAYMENT,
                PremiumPaymentCycle.MONTHLY, EFFECTIVE_AT.minusDays(1), PremiumPaymentStatus.UNPAID);
        return new PolicyCreatedEvent(
                POLICY_ID, new PolicyNo("P202608250001"), PolicyForm.INDIVIDUAL, "product-1",
                null, null, null, null, null,
                PolicyPeriod.of(EFFECTIVE_AT.minusYears(1), EFFECTIVE_AT.plusYears(10)),
                amount, amount, amount, List.of(mainProduct), premiumPlan, null, null, status,
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

    private static PolicyMaintenanceServiceRestriction restriction(
            PolicyServiceRestrictionType type,
            PolicyServiceRestrictionAction action) {
        return new PolicyMaintenanceServiceRestriction(type, action);
    }

    private static String summary(PolicyMaintenanceServiceRestriction restriction) {
        return "maintenance=maintenance-1;items=" + restriction.type().getCode()
                + ";fields=;action=NONE;restriction=" + restriction.action().getCode()
                + ":" + restriction.type().getCode();
    }
}
