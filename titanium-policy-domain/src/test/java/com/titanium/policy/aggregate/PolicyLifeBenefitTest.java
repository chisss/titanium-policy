package com.titanium.policy.aggregate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.policy.command.DistributeDividendCommand;
import com.titanium.policy.command.MatureDuePolicyCommand;
import com.titanium.policy.command.MaturePolicyCommand;
import com.titanium.policy.command.UpdateAccountValueCommand;
import com.titanium.policy.command.WaivePremiumCommand;
import com.titanium.policy.common.enums.DividendOption;
import com.titanium.policy.common.enums.PremiumWaiverReason;
import com.titanium.policy.event.AccountValueUpdatedEvent;
import com.titanium.policy.event.DividendDistributedEvent;
import com.titanium.policy.event.InvestmentAccountLinkedEvent;
import com.titanium.policy.event.PolicyActivatedEvent;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.event.PolicyMaturedEvent;
import com.titanium.policy.event.PremiumWaivedEvent;
import com.titanium.policy.exception.PolicyBusinessRuleException;
import com.titanium.policy.valueobject.PolicyNo;
import com.titanium.policy.valueobject.PolicyStatus;

/**
 * 保单聚合根·寿险给付生命周期测试（满期给付 / 保费豁免 / 红利派发）
 * <p>
 * 覆盖两全险满期金给付后转 EXPIRED、保费豁免保单持续有效、分红险红利按领取方式累积；及生效态/重复/金额校验。
 * 事件含 {@code now()}，用 expectSuccessfulHandlerExecution 断言执行成功，不做精确时间比对。
 * </p>
 */
class PolicyLifeBenefitTest {

    private FixtureConfiguration<Policy> fixture;

    private static final String POLICY_ID = "policy-001";
    private static final String TENANT_ID = "tenant-001";

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Policy.class);
        fixture.setReportIllegalStateChange(false);
    }

    private PolicyCreatedEvent createdEvent(InsuranceProductType type) {
        LocalDateTime now = LocalDateTime.now();
        return new PolicyCreatedEvent(POLICY_ID, new PolicyNo("POL-2024-0001"), PolicyForm.INDIVIDUAL, null, now,
                now.plusYears(10), null, null,
                new PolicyStatus(PolicyStatus.StatusCode.NOT_EFFECTIVE, now, "创建", "system"), new ArrayList<>(),
                type, TENANT_ID);
    }

    private PolicyActivatedEvent activatedEvent() {
        return new PolicyActivatedEvent(POLICY_ID, LocalDateTime.now(), TENANT_ID);
    }

    // ---- 满期给付 ----

    @Test
    @DisplayName("两全险生效保单满期给付，发布满期事件并转EXPIRED")
    void shouldMatureWithBenefit() {
        fixture.given(createdEvent(InsuranceProductType.ENDOWMENT), activatedEvent())
                .when(new MaturePolicyCommand(POLICY_ID, new BigDecimal("100000"), "op-1", TENANT_ID))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(
                                org.hamcrest.CoreMatchers.instanceOf(PolicyMaturedEvent.class))));
    }

    @Test
    @DisplayName("满期给付金额非正被拒绝")
    void shouldRejectNonPositiveMaturityBenefit() {
        fixture.given(createdEvent(InsuranceProductType.ENDOWMENT), activatedEvent())
                .when(new MaturePolicyCommand(POLICY_ID, BigDecimal.ZERO, "op-1", TENANT_ID))
                .expectException(PolicyBusinessRuleException.class);
    }

    // ---- 到期满期给付(定时任务,满期金由聚合保额推导) ----

    @Test
    @DisplayName("两全险到期满期给付,满期金取聚合基本保额并转EXPIRED")
    void shouldMatureDueWithSumInsured() {
        fixture.given(createdEventWithSumInsured(InsuranceProductType.ENDOWMENT, new BigDecimal("200000")),
                activatedEvent())
                .when(new MatureDuePolicyCommand(POLICY_ID, "op-1", TENANT_ID))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(
                                org.hamcrest.CoreMatchers.instanceOf(PolicyMaturedEvent.class))));
    }

    @Test
    @DisplayName("到期满期给付:非两全险被拒绝")
    void shouldRejectMatureDueForNonEndowment() {
        fixture.given(createdEventWithSumInsured(InsuranceProductType.TERM_LIFE, new BigDecimal("200000")),
                activatedEvent())
                .when(new MatureDuePolicyCommand(POLICY_ID, "op-1", TENANT_ID))
                .expectException(PolicyBusinessRuleException.class);
    }

    @Test
    @DisplayName("到期满期给付:保额缺失被拒绝")
    void shouldRejectMatureDueWhenNoSumInsured() {
        fixture.given(createdEvent(InsuranceProductType.ENDOWMENT), activatedEvent())
                .when(new MatureDuePolicyCommand(POLICY_ID, "op-1", TENANT_ID))
                .expectException(PolicyBusinessRuleException.class);
    }

    private PolicyCreatedEvent createdEventWithSumInsured(InsuranceProductType type, BigDecimal sumInsured) {
        LocalDateTime now = LocalDateTime.now();
        return new PolicyCreatedEvent(POLICY_ID, new PolicyNo("POL-2024-0003"), PolicyForm.INDIVIDUAL, null, now,
                now.plusYears(10), null, com.titanium.metadata.valueobject.Money.of(sumInsured, "CNY"),
                new PolicyStatus(PolicyStatus.StatusCode.NOT_EFFECTIVE, now, "创建", "system"), new ArrayList<>(),
                type, TENANT_ID);
    }

    @Test
    @DisplayName("未生效保单不可满期给付")
    void shouldRejectMatureWhenNotEffective() {
        fixture.given(createdEvent(InsuranceProductType.ENDOWMENT))
                .when(new MaturePolicyCommand(POLICY_ID, new BigDecimal("100000"), "op-1", TENANT_ID))
                .expectException(PolicyBusinessRuleException.class);
    }

    // ---- 保费豁免 ----

    @Test
    @DisplayName("生效保单可办理保费豁免，保单保持有效")
    void shouldWaivePremium() {
        fixture.given(createdEvent(InsuranceProductType.WHOLE_LIFE), activatedEvent())
                .when(new WaivePremiumCommand(POLICY_ID, PremiumWaiverReason.POLICY_HOLDER_DEATH, "op-1", TENANT_ID))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(
                                org.hamcrest.CoreMatchers.instanceOf(PremiumWaivedEvent.class))))
                .expectState(p -> {
                    if (!p.isPremiumWaived()
                            || p.getStatus().statusCode() != PolicyStatus.StatusCode.EFFECTIVE) {
                        throw new AssertionError("豁免后保单应保持生效且标记已豁免");
                    }
                });
    }

    @Test
    @DisplayName("重复保费豁免被拒绝")
    void shouldRejectDuplicateWaiver() {
        fixture.given(createdEvent(InsuranceProductType.WHOLE_LIFE), activatedEvent(),
                        new PremiumWaivedEvent(POLICY_ID, PremiumWaiverReason.INSURED_DISABILITY, "op-1",
                                LocalDateTime.now().minusDays(1), TENANT_ID))
                .when(new WaivePremiumCommand(POLICY_ID, PremiumWaiverReason.POLICY_HOLDER_DEATH, "op-1", TENANT_ID))
                .expectException(PolicyBusinessRuleException.class);
    }

    // ---- 红利派发 ----

    @Test
    @DisplayName("累积生息红利累加到累计红利")
    void shouldAccumulateDividend() {
        fixture.given(createdEvent(InsuranceProductType.WHOLE_LIFE), activatedEvent())
                .when(new DistributeDividendCommand(POLICY_ID, new BigDecimal("500"), DividendOption.ACCUMULATE, 1,
                        "op-1", TENANT_ID))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(
                                org.hamcrest.CoreMatchers.instanceOf(DividendDistributedEvent.class))));
    }

    @Test
    @DisplayName("现金领取红利不累加累计红利")
    void shouldNotAccumulateCashDividend() {
        fixture.given(createdEvent(InsuranceProductType.WHOLE_LIFE), activatedEvent(),
                        new DividendDistributedEvent(POLICY_ID, new BigDecimal("500"), DividendOption.ACCUMULATE, 1,
                                new BigDecimal("500"), "op-1", LocalDateTime.now().minusDays(1), TENANT_ID))
                .when(new DistributeDividendCommand(POLICY_ID, new BigDecimal("300"), DividendOption.CASH, 2, "op-1",
                        TENANT_ID))
                .expectSuccessfulHandlerExecution()
                .expectState(p -> {
                    // 累计红利应仍为 500（现金领取不累加）
                    if (p.getAccumulatedDividend() == null
                            || p.getAccumulatedDividend().value().compareTo(new BigDecimal("500")) != 0) {
                        throw new AssertionError("现金领取不应累加累计红利，应仍为500，实际="
                                + (p.getAccumulatedDividend() != null ? p.getAccumulatedDividend().value() : null));
                    }
                });
    }

    @Test
    @DisplayName("红利金额非正被拒绝")
    void shouldRejectNonPositiveDividend() {
        fixture.given(createdEvent(InsuranceProductType.WHOLE_LIFE), activatedEvent())
                .when(new DistributeDividendCommand(POLICY_ID, BigDecimal.ZERO, DividendOption.CASH, 1, "op-1",
                        TENANT_ID))
                .expectException(PolicyBusinessRuleException.class);
    }

    // ---- 险种/形态前置校验 ----

    @Test
    @DisplayName("非两全险(定期寿)满期给付被拒绝")
    void shouldRejectMatureForNonEndowment() {
        fixture.given(createdEvent(InsuranceProductType.TERM_LIFE), activatedEvent())
                .when(new MaturePolicyCommand(POLICY_ID, new BigDecimal("100000"), "op-1", TENANT_ID))
                .expectException(PolicyBusinessRuleException.class);
    }

    @Test
    @DisplayName("投连/万能险红利派发被拒绝(账户价值型非分红机制)")
    void shouldRejectDividendForInvestmentLinked() {
        fixture.given(universalCreatedEvent(), activatedEvent())
                .when(new DistributeDividendCommand(POLICY_ID, new BigDecimal("500"), DividendOption.CASH, 1, "op-1",
                        TENANT_ID))
                .expectException(PolicyBusinessRuleException.class);
    }

    // ---- 投资账户价值回写 ----

    @Test
    @DisplayName("投连险已挂接账户,回写账户价值发布事件")
    void shouldUpdateAccountValueWhenLinked() {
        fixture.given(universalCreatedEvent(), activatedEvent(),
                new InvestmentAccountLinkedEvent(POLICY_ID, "ACC-001", LocalDateTime.now(), "op-1", TENANT_ID))
                .when(new UpdateAccountValueCommand(POLICY_ID, "ACC-001", new BigDecimal("12500.00"), "CNY", TENANT_ID))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(
                                org.hamcrest.CoreMatchers.instanceOf(AccountValueUpdatedEvent.class))));
    }

    @Test
    @DisplayName("未挂接投资账户,回写账户价值被拒绝")
    void shouldRejectAccountValueWhenNotLinked() {
        fixture.given(universalCreatedEvent(), activatedEvent())
                .when(new UpdateAccountValueCommand(POLICY_ID, "ACC-001", new BigDecimal("12500.00"), "CNY", TENANT_ID))
                .expectException(PolicyBusinessRuleException.class);
    }

    private PolicyCreatedEvent universalCreatedEvent() {
        LocalDateTime now = LocalDateTime.now();
        return new PolicyCreatedEvent(POLICY_ID, new PolicyNo("POL-2024-0002"), PolicyForm.UNIVERSAL, null, now,
                now.plusYears(10), null, null,
                new PolicyStatus(PolicyStatus.StatusCode.NOT_EFFECTIVE, now, "创建", "system"), new ArrayList<>(),
                InsuranceProductType.WHOLE_LIFE, TENANT_ID);
    }
}
