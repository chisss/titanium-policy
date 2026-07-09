package com.titanium.policy.aggregate;

import java.time.LocalDateTime;
import java.util.ArrayList;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.customer.CustomerEnum.CustomerGender;
import com.titanium.metadata.enums.customer.CustomerEnum.IdCardType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.policy.command.AddInsuredMemberCommand;
import com.titanium.policy.command.LinkInvestmentAccountCommand;
import com.titanium.policy.command.RemoveInsuredMemberCommand;
import com.titanium.policy.common.enums.FamilyRelation;
import com.titanium.policy.entity.insurance.InsuredPartyList.InsuredInfo;
import com.titanium.policy.event.InsuredMemberAddedEvent;
import com.titanium.policy.event.InsuredMemberRemovedEvent;
import com.titanium.policy.event.InvestmentAccountLinkedEvent;
import com.titanium.policy.event.PolicyActivatedEvent;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.exception.PolicyBusinessRuleException;
import com.titanium.policy.valueobject.PolicyNo;
import com.titanium.policy.valueobject.PolicyStatus;

/**
 * 保单聚合根·形态行为测试（阶段四 4.4/4.5/4.6）
 * <p>
 * 覆盖投连账户挂接（仅投连/万能形态）、团单/家庭险被保险人动态增减（仅生效保单）、
 * 形态准入校验（非投连不可挂账户、非团单/家庭不可增减成员）。
 * </p>
 */
class PolicyFormBehaviorTest {

    private FixtureConfiguration<Policy> fixture;

    private static final String POLICY_ID = "policy-001";
    private static final String TENANT_ID = "tenant-001";

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Policy.class);
        fixture.setReportIllegalStateChange(false);
    }

    /** 指定形态的保单创建事件 */
    private PolicyCreatedEvent createdEvent(PolicyForm form) {
        LocalDateTime now = LocalDateTime.now();
        return new PolicyCreatedEvent(POLICY_ID, new PolicyNo("POL-2024-0001"), form, null, now, now.plusYears(1), null,
                null, new PolicyStatus(PolicyStatus.StatusCode.NOT_EFFECTIVE, now, "创建", "system"), new ArrayList<>(),
                TENANT_ID);
    }

    private PolicyActivatedEvent activatedEvent() {
        return new PolicyActivatedEvent(POLICY_ID, LocalDateTime.now(), TENANT_ID);
    }

    private InsuredInfo member(String id) {
        return new InsuredInfo(id, "张三", IdCardType.CHINA_ID_CARD, "3301**********1234", 30, CustomerGender.MALE,
                null);
    }

    @Test
    @DisplayName("投连保单可挂接投资账户")
    void shouldLinkInvestmentAccountForInvestmentLinkedPolicy() {
        fixture.given(createdEvent(PolicyForm.INVESTMENT_LINKED))
                .when(new LinkInvestmentAccountCommand(POLICY_ID, "INV-001", "op-1", TENANT_ID))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(
                                org.hamcrest.CoreMatchers.instanceOf(InvestmentAccountLinkedEvent.class))));
    }

    @Test
    @DisplayName("非投连保单挂接投资账户被拒绝")
    void shouldRejectLinkAccountForNonInvestmentPolicy() {
        fixture.given(createdEvent(PolicyForm.INDIVIDUAL))
                .when(new LinkInvestmentAccountCommand(POLICY_ID, "INV-001", "op-1", TENANT_ID))
                .expectException(PolicyBusinessRuleException.class);
    }

    @Test
    @DisplayName("重复挂接投资账户幂等，不再产生事件")
    void shouldBeIdempotentOnDuplicateLink() {
        fixture.given(createdEvent(PolicyForm.UNIVERSAL),
                new InvestmentAccountLinkedEvent(POLICY_ID, "INV-001", LocalDateTime.now(), "op-1", TENANT_ID))
                .when(new LinkInvestmentAccountCommand(POLICY_ID, "INV-002", "op-1", TENANT_ID))
                .expectSuccessfulHandlerExecution()
                .expectNoEvents();
    }

    @Test
    @DisplayName("团单生效后可加保新成员")
    void shouldAddMemberForEffectiveGroupPolicy() {
        fixture.given(createdEvent(PolicyForm.GROUP), activatedEvent())
                .when(new AddInsuredMemberCommand(POLICY_ID, member("insured-2"), null, "op-1", TENANT_ID))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(
                                org.hamcrest.CoreMatchers.instanceOf(InsuredMemberAddedEvent.class))));
    }

    @Test
    @DisplayName("家庭险加保未指定家庭关系被拒绝")
    void shouldRejectFamilyMemberWithoutRelation() {
        fixture.given(createdEvent(PolicyForm.FAMILY), activatedEvent())
                .when(new AddInsuredMemberCommand(POLICY_ID, member("insured-2"), null, "op-1", TENANT_ID))
                .expectException(PolicyBusinessRuleException.class);
    }

    @Test
    @DisplayName("家庭险指定家庭关系可加保")
    void shouldAddFamilyMemberWithRelation() {
        fixture.given(createdEvent(PolicyForm.FAMILY), activatedEvent())
                .when(new AddInsuredMemberCommand(POLICY_ID, member("insured-2"), FamilyRelation.SPOUSE, "op-1",
                        TENANT_ID))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(
                                org.hamcrest.CoreMatchers.instanceOf(InsuredMemberAddedEvent.class))));
    }

    @Test
    @DisplayName("个险不可增减被保险人")
    void shouldRejectMemberChangeForIndividualPolicy() {
        fixture.given(createdEvent(PolicyForm.INDIVIDUAL), activatedEvent())
                .when(new AddInsuredMemberCommand(POLICY_ID, member("insured-2"), null, "op-1", TENANT_ID))
                .expectException(PolicyBusinessRuleException.class);
    }

    @Test
    @DisplayName("未生效保单不可加保")
    void shouldRejectMemberAddWhenNotEffective() {
        fixture.given(createdEvent(PolicyForm.GROUP))
                .when(new AddInsuredMemberCommand(POLICY_ID, member("insured-2"), null, "op-1", TENANT_ID))
                .expectException(PolicyBusinessRuleException.class);
    }

    @Test
    @DisplayName("团单减保：移除已有成员")
    void shouldRemoveMemberForGroupPolicy() {
        // 先加两名成员再移除其一（移除后至少剩 1 名）
        InsuredInfo m1 = member("insured-1");
        InsuredInfo m2 = member("insured-2");
        fixture.given(createdEvent(PolicyForm.GROUP), activatedEvent(),
                new InsuredMemberAddedEvent(POLICY_ID, m1, null, LocalDateTime.now(), "op-1", TENANT_ID),
                new InsuredMemberAddedEvent(POLICY_ID, m2, null, LocalDateTime.now(), "op-1", TENANT_ID))
                .when(new RemoveInsuredMemberCommand(POLICY_ID, "insured-1", "离职", "op-1", TENANT_ID))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(
                                org.hamcrest.CoreMatchers.instanceOf(InsuredMemberRemovedEvent.class))));
    }

    @Test
    @DisplayName("移除不存在的成员被拒绝")
    void shouldRejectRemoveNonexistentMember() {
        fixture.given(createdEvent(PolicyForm.GROUP), activatedEvent(),
                new InsuredMemberAddedEvent(POLICY_ID, member("insured-1"), null, LocalDateTime.now(), "op-1",
                        TENANT_ID))
                .when(new RemoveInsuredMemberCommand(POLICY_ID, "insured-999", "误删", "op-1", TENANT_ID))
                .expectException(PolicyBusinessRuleException.class);
    }
}
