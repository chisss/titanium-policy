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
import com.titanium.policy.command.LinkSubPolicyCommand;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.event.SubPolicyLinkedEvent;
import com.titanium.policy.valueobject.PolicyNo;
import com.titanium.policy.valueobject.PolicyStatus;

/**
 * 保单聚合根·团单主子联动测试
 * <p>
 * 验证挂载子保单经事件溯源落地：独立保单升级为父保单、子单计数累加。
 * </p>
 */
class PolicyLinkSubPolicyTest {

    private FixtureConfiguration<Policy> fixture;

    private static final String PARENT_ID = "parent-001";
    private static final String TENANT_ID = "tenant-001";

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Policy.class);
        // Policy 的 on(PolicyCreatedEvent) 用 LocalDateTime.now() 赋值 createTime（非确定性），
        // 关闭非法状态变更检测，聚焦验证联动逻辑本身
        fixture.setReportIllegalStateChange(false);
    }

    private PolicyCreatedEvent createdEvent() {
        LocalDateTime now = LocalDateTime.now();
        return new PolicyCreatedEvent(PARENT_ID, new PolicyNo("POL-2024-0001"), PolicyForm.GROUP, null, now,
                now.plusYears(1), null, null,
                new PolicyStatus(PolicyStatus.StatusCode.NOT_EFFECTIVE, now, "创建", "system"),
                Collections.emptyList(), TENANT_ID);
    }

    /** 匹配指定 childId 与子单计数的 SubPolicyLinkedEvent（忽略时间戳） */
    private TypeSafeMatcher<SubPolicyLinkedEvent> linkedEvent(String childId, int expectedCount) {
        return new TypeSafeMatcher<>() {
            @Override
            protected boolean matchesSafely(SubPolicyLinkedEvent e) {
                return childId.equals(e.childPolicyId()) && e.subPolicyCount() == expectedCount
                        && PARENT_ID.equals(e.parentPolicyId());
            }

            @Override
            public void describeTo(Description d) {
                d.appendText("SubPolicyLinkedEvent[child=" + childId + ", count=" + expectedCount + "]");
            }
        };
    }

    @Test
    void shouldLinkFirstSubPolicy() {
        // 独立保单挂载首个子保单 → 升级为父保单，子单计数=1
        fixture.given(createdEvent())
                .when(new LinkSubPolicyCommand(PARENT_ID, "child-001", "group-1", "op-1", TENANT_ID))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(linkedEvent("child-001", 1))));
    }

    @Test
    void shouldAccumulateSubPolicyCount() {
        // 已挂载1个子单后再挂载 → 计数=2
        fixture.given(createdEvent(),
                new SubPolicyLinkedEvent(PARENT_ID, "child-001", "group-1", 1, LocalDateTime.now(), "op-1", TENANT_ID))
                .when(new LinkSubPolicyCommand(PARENT_ID, "child-002", "group-1", "op-1", TENANT_ID))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(linkedEvent("child-002", 2))));
    }
}
