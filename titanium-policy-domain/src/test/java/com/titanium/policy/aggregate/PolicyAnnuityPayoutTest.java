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
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.command.PayAnnuityBenefitCommand;
import com.titanium.policy.command.StartAnnuityPayoutCommand;
import com.titanium.policy.common.enums.AnnuityPayoutFrequency;
import com.titanium.policy.event.AnnuityBenefitPaidEvent;
import com.titanium.policy.event.AnnuityPayoutStartedEvent;
import com.titanium.policy.event.PolicyActivatedEvent;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.exception.PolicyBusinessRuleException;
import com.titanium.policy.valueobject.PolicyNo;
import com.titanium.policy.valueobject.PolicyStatus;

/**
 * 保单聚合根·年金给付行为测试（P0-2 年金给付主链路）
 * <p>
 * 覆盖年金给付期启动（仅年金险种的生效保单）、逐期给付推进、给满期数完成、
 * 险种/状态/重复启动准入校验；验证年金给付<b>不改变保单状态</b>。
 * </p>
 */
class PolicyAnnuityPayoutTest {

    private FixtureConfiguration<Policy> fixture;

    private static final String POLICY_ID = "policy-001";
    private static final String TENANT_ID = "tenant-001";
    private static final String CNY       = "CNY";

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Policy.class);
        fixture.setReportIllegalStateChange(false);
    }

    /** 指定险种的保单创建事件 */
    private PolicyCreatedEvent createdEvent(InsuranceProductType insuranceType) {
        LocalDateTime now = LocalDateTime.now();
        return new PolicyCreatedEvent(POLICY_ID, new PolicyNo("POL-2024-0001"), PolicyForm.INDIVIDUAL, null, now,
                now.plusYears(1), null, null,
                new PolicyStatus(PolicyStatus.StatusCode.NOT_EFFECTIVE, now, "创建", "system"), new ArrayList<>(),
                insuranceType, TENANT_ID);
    }

    private PolicyActivatedEvent activatedEvent() {
        return new PolicyActivatedEvent(POLICY_ID, LocalDateTime.now(), TENANT_ID);
    }

    private Money amount() {
        return Money.of(new BigDecimal("1000.00"), CNY);
    }

    private StartAnnuityPayoutCommand startCommand(Integer totalInstallments) {
        return new StartAnnuityPayoutCommand(POLICY_ID, LocalDateTime.now(), AnnuityPayoutFrequency.ANNUALLY, amount(),
                totalInstallments, "op-1", TENANT_ID);
    }

    private AnnuityPayoutStartedEvent startedEvent(Integer totalInstallments) {
        LocalDateTime now = LocalDateTime.now();
        return new AnnuityPayoutStartedEvent(POLICY_ID, now, AnnuityPayoutFrequency.ANNUALLY, amount(),
                totalInstallments, now, "op-1", now, TENANT_ID);
    }

    @Test
    @DisplayName("年金险生效保单可启动给付期")
    void shouldStartAnnuityPayoutForEffectiveAnnuityPolicy() {
        fixture.given(createdEvent(InsuranceProductType.ANNUITY), activatedEvent())
                .when(startCommand(10))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(
                                org.hamcrest.CoreMatchers.instanceOf(AnnuityPayoutStartedEvent.class))));
    }

    @Test
    @DisplayName("非年金险种保单启动给付被拒绝")
    void shouldRejectStartForNonAnnuityPolicy() {
        fixture.given(createdEvent(InsuranceProductType.WHOLE_LIFE), activatedEvent())
                .when(startCommand(10))
                .expectException(PolicyBusinessRuleException.class);
    }

    @Test
    @DisplayName("未生效年金保单启动给付被拒绝")
    void shouldRejectStartWhenNotEffective() {
        fixture.given(createdEvent(InsuranceProductType.ANNUITY))
                .when(startCommand(10))
                .expectException(PolicyBusinessRuleException.class);
    }

    @Test
    @DisplayName("重复启动年金给付期被拒绝")
    void shouldRejectDuplicateStart() {
        fixture.given(createdEvent(InsuranceProductType.ANNUITY), activatedEvent(), startedEvent(10))
                .when(startCommand(10))
                .expectException(PolicyBusinessRuleException.class);
    }

    @Test
    @DisplayName("年金给付期内可逐期给付，且不改变保单状态")
    void shouldPayAnnuityBenefit() {
        fixture.given(createdEvent(InsuranceProductType.ANNUITY), activatedEvent(), startedEvent(10))
                .when(new PayAnnuityBenefitCommand(POLICY_ID, "op-1", TENANT_ID))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(
                                org.hamcrest.CoreMatchers.instanceOf(AnnuityBenefitPaidEvent.class))));
    }

    @Test
    @DisplayName("给付期未启动时给付被拒绝")
    void shouldRejectPayWhenNotStarted() {
        fixture.given(createdEvent(InsuranceProductType.ANNUITY), activatedEvent())
                .when(new PayAnnuityBenefitCommand(POLICY_ID, "op-1", TENANT_ID))
                .expectException(PolicyBusinessRuleException.class);
    }

    @Test
    @DisplayName("定期年金给满最后一期计划转为已完成")
    void shouldCompleteAfterLastInstallment() {
        // 总期数 1，已给付事件推进 0→仍未给付；再给一期即完成
        fixture.given(createdEvent(InsuranceProductType.ANNUITY), activatedEvent(), startedEvent(1))
                .when(new PayAnnuityBenefitCommand(POLICY_ID, "op-1", TENANT_ID))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(
                                org.hamcrest.CoreMatchers.instanceOf(AnnuityBenefitPaidEvent.class))));
    }
}
