package com.titanium.policy.aggregate;

import java.time.LocalDateTime;
import java.util.Collections;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.policy.command.ApplyPolicyEndorsementCommand;
import com.titanium.policy.common.enums.PolicyDataUpdateType;
import com.titanium.policy.event.PolicyActivatedEvent;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.event.PolicyEndorsedEvent;
import com.titanium.policy.exception.PolicyBusinessRuleException;
import com.titanium.policy.valueobject.PolicyNo;
import com.titanium.policy.valueobject.PolicyStatus;

/**
 * 保单聚合根·批改回写测试
 * <p>
 * 验证数据/要素类批改:仅 EFFECTIVE 可批改、apply PolicyEndorsedEvent、版本号递增、非生效态拒绝。
 * </p>
 */
class PolicyEndorsementTest {

    private FixtureConfiguration<Policy> fixture;

    private static final String POLICY_ID = "policy-001";
    private static final String TENANT_ID = "tenant-001";

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Policy.class);
        // on(PolicyCreatedEvent) 用 LocalDateTime.now() 赋 createTime（非确定性）
        fixture.setReportIllegalStateChange(false);
    }

    private PolicyCreatedEvent createdEvent() {
        LocalDateTime now = LocalDateTime.now();
        return new PolicyCreatedEvent(POLICY_ID, new PolicyNo("POL-2024-0001"), PolicyForm.INDIVIDUAL, null,
                now.minusDays(1), now.plusYears(1), null, null,
                new PolicyStatus(PolicyStatus.StatusCode.NOT_EFFECTIVE, now, "创建", "system"),
                Collections.emptyList(), null, null, TENANT_ID);
    }

    /** 匹配指定批改类型的 PolicyEndorsedEvent（忽略时间戳） */
    private TypeSafeMatcher<PolicyEndorsedEvent> endorsedEvent(PolicyDataUpdateType type, int versionAfter) {
        return new TypeSafeMatcher<>() {
            @Override
            protected boolean matchesSafely(PolicyEndorsedEvent e) {
                return e.updateType() == type && e.versionAfter() == versionAfter
                        && "ED-1".equals(e.endorsementNo()) && POLICY_ID.equals(e.policyId());
            }

            @Override
            public void describeTo(Description d) {
                d.appendText("PolicyEndorsedEvent[type=" + type + ", versionAfter=" + versionAfter + "]");
            }
        };
    }

    private ApplyPolicyEndorsementCommand endorseCmd(PolicyDataUpdateType type) {
        return new ApplyPolicyEndorsementCommand(POLICY_ID, "ED-1", type, LocalDateTime.now(), "受益人变更为张三", null,
                "M-1", "op-1", TENANT_ID);
    }

    @Test
    void shouldEndorseEffectivePolicy() {
        // 生效保单受益人变更 → 批改成功，版本号从 0 递增为 1
        fixture.given(createdEvent(), new PolicyActivatedEvent(POLICY_ID, LocalDateTime.now(), TENANT_ID))
                .when(endorseCmd(PolicyDataUpdateType.BENEFICIARY_CHANGE))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(
                                endorsedEvent(PolicyDataUpdateType.BENEFICIARY_CHANGE, 1))));
    }

    @Test
    void shouldRejectEndorsementOnNotEffectivePolicy() {
        // 未生效保单不可批改
        fixture.given(createdEvent())
                .when(endorseCmd(PolicyDataUpdateType.BENEFICIARY_CHANGE))
                .expectException(PolicyBusinessRuleException.class);
    }

    @Test
    void shouldAccumulateVersionAcrossEndorsements() {
        // 已批改一次（版本=1）后再批改 → 版本=2
        fixture.given(createdEvent(), new PolicyActivatedEvent(POLICY_ID, LocalDateTime.now(), TENANT_ID),
                new PolicyEndorsedEvent(POLICY_ID, "ED-0", PolicyDataUpdateType.HOLDER_CHANGE,
                        PolicyDataUpdateType.HOLDER_CHANGE.getCategory(), 1, LocalDateTime.now(), "投保人变更", null, false,
                        "M-0", LocalDateTime.now(), "op-0", TENANT_ID))
                .when(endorseCmd(PolicyDataUpdateType.BENEFICIARY_CHANGE))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(
                                endorsedEvent(PolicyDataUpdateType.BENEFICIARY_CHANGE, 2))));
    }

    @Test
    void shouldRejectDuplicateEndorsementFromSameMaintenance() {
        // 幂等：同一来源保全案件(M-1)重复批改被拒（Kafka at-least-once 重投兜底）
        fixture.given(createdEvent(), new PolicyActivatedEvent(POLICY_ID, LocalDateTime.now(), TENANT_ID),
                new PolicyEndorsedEvent(POLICY_ID, "ED-1", PolicyDataUpdateType.BENEFICIARY_CHANGE,
                        PolicyDataUpdateType.BENEFICIARY_CHANGE.getCategory(), 1, LocalDateTime.now(), "受益人变更", null,
                        false, "M-1", LocalDateTime.now(), "op-1", TENANT_ID))
                .when(endorseCmd(PolicyDataUpdateType.BENEFICIARY_CHANGE))
                .expectException(PolicyBusinessRuleException.class);
    }
}
