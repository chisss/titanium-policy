package com.titanium.policy.aggregate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.PolicyEnum.PolicyStatus;
import com.titanium.policy.command.ActivatePolicyCommand;
import com.titanium.policy.command.CreatePolicyCommand;
import com.titanium.policy.entity.PolicyItem;
import com.titanium.policy.event.PolicyActivatedEvent;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.valueobject.Amount;
import com.titanium.policy.valueobject.Coverage;
import com.titanium.policy.valueobject.PolicyNo;

class PolicyTest {

    private FixtureConfiguration<Policy> fixture;
    private Clock                        fixedClock;
    private LocalDateTime                fixedTime;

    @BeforeEach
    void setUp() {
        // 首先创建固定时钟，使用当前时刻
        fixedClock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        // 然后从固定时钟获取fixedTime，确保两者完全同步
        fixedTime = LocalDateTime.now(fixedClock);

        fixture = new AggregateTestFixture<>(Policy.class);
        // 注册固定时钟
        fixture.registerInjectableResource(fixedClock);
    }

    @Test
    void testCreatePolicy() {
        // 1. 测试参数准备
        String policyId = "policy-001";
        PolicyNo policyNumber = new PolicyNo("POL-2024-0001");
        String customerId = "customer-001";
        String productId = "product-001";
        LocalDateTime effectiveDate = fixedTime;
        LocalDateTime expiryDate = effectiveDate.plusYears(1);

        Amount premium = new Amount(new BigDecimal("1000.00"), "CNY"); // 保费值对象
        List<PolicyItem> policyItems = Collections.singletonList(new PolicyItem("item-001", "product-001",
                new Coverage("COV-001", "Basic Coverage", "Basic Coverage", true),
                new Amount(new BigDecimal("1000.00"), "CNY"), premium, 100, 20)); // 订单项
        String tenantId = "tenant-001"; // 租户ID

        // 2. 执行测试
        fixture.givenNoPriorActivity()
                .when(new CreatePolicyCommand(policyId, policyNumber, customerId, productId, effectiveDate, expiryDate,
                        premium, policyItems, tenantId))
                .expectEvents(new PolicyCreatedEvent(policyId, policyNumber, customerId, productId, effectiveDate,
                        expiryDate, premium, PolicyStatus.PENDING, policyItems, tenantId));
    }

    @Test
    void testActivatePolicy() {
        String policyId = "policy-001";
        PolicyNo policyNumber = new PolicyNo("POL-2024-0001");
        String customerId = "customer-001";
        String productId = "product-001";
        LocalDateTime effectiveDate = fixedTime.minusDays(1); // 生效日期已过
        LocalDateTime expiryDate = effectiveDate.plusYears(1);
        String tenantId = "tenant-001";

        Amount premium = new Amount(new BigDecimal("1000.00"), "CNY");
        List<PolicyItem> policyItems = Collections.emptyList();

        fixture.given(new PolicyCreatedEvent(policyId, policyNumber, customerId, productId, effectiveDate, expiryDate,
                premium, PolicyStatus.PENDING, policyItems, tenantId))
                .when(new ActivatePolicyCommand(policyId, tenantId))
                // 使用事件匹配器验证激活事件
                .expectEvents(new PolicyActivatedEvent(policyId, effectiveDate, tenantId));
    }

    @Test
    void testCannotActivatePolicyBeforeEffectiveDate() {
        String policyId = "policy-001";
        PolicyNo policyNumber = new PolicyNo("POL-2024-0001");
        String customerId = "customer-001";
        String productId = "product-001";
        LocalDateTime effectiveDate = fixedTime.plusDays(1); // 生效日期未到
        LocalDateTime expiryDate = effectiveDate.plusYears(1);
        String tenantId = "tenant-001";

        Amount premium = new Amount(new BigDecimal("1000.00"), "CNY");
        List<PolicyItem> policyItems = Collections.emptyList();

        fixture.given(new PolicyCreatedEvent(policyId, policyNumber, customerId, productId, effectiveDate, expiryDate,
                premium, PolicyStatus.PENDING, policyItems, tenantId))
                .when(new ActivatePolicyCommand(policyId, tenantId)).expectException(IllegalArgumentException.class);
    }

    @Test
    void testCannotActivateNonPendingPolicy() {
        String policyId = "policy-001";
        PolicyNo policyNumber = new PolicyNo("POL-2024-0001");
        String customerId = "customer-001";
        String productId = "product-001";
        LocalDateTime effectiveDate = fixedTime.minusDays(1);
        LocalDateTime expiryDate = effectiveDate.plusYears(1);
        String tenantId = "tenant-001";

        Amount premium = new Amount(new BigDecimal("1000.00"), "CNY");
        List<PolicyItem> policyItems = Collections.emptyList();

        fixture.given(
                new PolicyCreatedEvent(policyId, policyNumber, customerId, productId, effectiveDate, expiryDate,
                        premium, PolicyStatus.PENDING, policyItems, tenantId),
                new PolicyActivatedEvent(policyId, fixedTime, tenantId))
                .when(new ActivatePolicyCommand(policyId, tenantId)).expectException(IllegalArgumentException.class);
    }
}
