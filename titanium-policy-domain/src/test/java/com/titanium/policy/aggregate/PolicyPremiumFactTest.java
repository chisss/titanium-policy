package com.titanium.policy.aggregate;

import static org.axonframework.test.matchers.Matchers.exactSequenceOf;
import static org.axonframework.test.matchers.Matchers.payloadsMatching;
import static org.axonframework.test.matchers.Matchers.predicate;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.command.CreatePolicyCommand;
import com.titanium.policy.command.CreatePolicyDirectlyCommand;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.service.PolicyCompositionDomainService;
import com.titanium.policy.valueobject.policy.PolicyPeriod;

/**
 * 保单创建事件的标准保费与应付保费事实测试。
 */
class PolicyPremiumFactTest {

    private FixtureConfiguration<Policy> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Policy.class);
        fixture.registerInjectableResource(mock(PolicyCompositionDomainService.class));
        fixture.setReportIllegalStateChange(false);
    }

    @Test
    void underwritingPolicyCarriesStandardAndPayablePremium() {
        Money standardPremium = Money.of(new BigDecimal("1000.00"), "CNY");
        Money payablePremium = Money.of(new BigDecimal("1200.00"), "CNY");
        CreatePolicyCommand command = new CreatePolicyCommand("POLICY_001", "POL001", "INSURANCE_001", null,
                "UW_001", "BIZ_001", null, PolicyForm.INDIVIDUAL, "PRODUCT_001", null, null, List.of(), null,
                standardPremium, payablePremium, period(), null, null, null, null, "TENANT_001");

        fixture.givenNoPriorActivity()
                .when(command)
                .expectEventsMatching(payloadsMatching(exactSequenceOf(
                        predicate(payload -> payload instanceof PolicyCreatedEvent event
                                && standardPremium.equals(event.standardPremium())
                                && payablePremium.equals(event.premium())))));
    }

    @Test
    void oneStepPolicyUsesTotalPremiumAsStandardPremium() {
        Money premium = Money.of(new BigDecimal("800.00"), "CNY");
        CreatePolicyDirectlyCommand command = new CreatePolicyDirectlyCommand("POLICY_002", "POL002", "BIZ_002",
                null, PolicyForm.INDIVIDUAL, "PRODUCT_001", null, List.of(), null, premium, period(), null, null,
                null, null, "TENANT_001");

        fixture.givenNoPriorActivity()
                .when(command)
                .expectEventsMatching(payloadsMatching(exactSequenceOf(
                        predicate(payload -> payload instanceof PolicyCreatedEvent event
                                && premium.equals(event.standardPremium())
                                && premium.equals(event.premium())))));
    }

    private PolicyPeriod period() {
        LocalDateTime now = LocalDateTime.now();
        return PolicyPeriod.of(now, now.plusYears(1), 0, 0);
    }
}
