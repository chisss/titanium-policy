package com.titanium.policy.aggregate;

import static org.axonframework.test.matchers.Matchers.exactSequenceOf;
import static org.axonframework.test.matchers.Matchers.payloadsMatching;
import static org.hamcrest.Matchers.instanceOf;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.billing.PremiumCollectionMode;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.command.AssociatePremiumBillingCommand;
import com.titanium.policy.common.enums.PolicyStatusCode;
import com.titanium.policy.common.enums.PremiumCollectionStatus;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.event.PremiumBillingAssociatedEvent;
import com.titanium.policy.exception.PolicyBusinessRuleException;
import com.titanium.policy.valueobject.PolicyNo;
import com.titanium.policy.valueobject.PolicyStatus;
import com.titanium.policy.valueobject.policy.CollectionInfo;
import com.titanium.policy.valueobject.policy.PolicyPeriod;

/**
 * 保单收费单据关联的事件溯源测试。
 */
class PolicyPremiumBillingAssociationTest {

    private static final String POLICY_ID = "POLICY_001";
    private static final String TENANT_ID = "TENANT_001";

    private FixtureConfiguration<Policy> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Policy.class);
        fixture.setReportIllegalStateChange(false);
    }

    @Test
    void associatesBillingDocumentsWithoutRecordingCollection() {
        fixture.given(createdEvent())
                .when(new AssociatePremiumBillingCommand(POLICY_ID, "BILL_001", "PAY_001", TENANT_ID))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(payloadsMatching(exactSequenceOf(
                        instanceOf(PremiumBillingAssociatedEvent.class))));
    }

    @Test
    void repeatedSameAssociationIsIdempotent() {
        PremiumBillingAssociatedEvent associated = new PremiumBillingAssociatedEvent(POLICY_ID, "BIZ_001",
                "BILL_001", "PAY_001", PremiumCollectionStatus.UNCOLLECTED, TENANT_ID);

        fixture.given(createdEvent(), associated)
                .when(new AssociatePremiumBillingCommand(POLICY_ID, "BILL_001", "PAY_001", TENANT_ID))
                .expectSuccessfulHandlerExecution()
                .expectNoEvents();
    }

    @Test
    void enrichesExistingBillWithPaymentOrder() {
        PremiumBillingAssociatedEvent billAssociated = new PremiumBillingAssociatedEvent(POLICY_ID, "BIZ_001",
                "BILL_001", null, PremiumCollectionStatus.UNCOLLECTED, TENANT_ID);

        fixture.given(createdEvent(), billAssociated)
                .when(new AssociatePremiumBillingCommand(POLICY_ID, "BILL_001", "PAY_001", TENANT_ID))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(payloadsMatching(exactSequenceOf(
                        instanceOf(PremiumBillingAssociatedEvent.class))));
    }

    @Test
    void rejectsRebindingToAnotherBill() {
        PremiumBillingAssociatedEvent associated = new PremiumBillingAssociatedEvent(POLICY_ID, "BIZ_001",
                "BILL_001", null, PremiumCollectionStatus.UNCOLLECTED, TENANT_ID);

        fixture.given(createdEvent(), associated)
                .when(new AssociatePremiumBillingCommand(POLICY_ID, "BILL_002", null, TENANT_ID))
                .expectException(PolicyBusinessRuleException.class);
    }

    private PolicyCreatedEvent createdEvent() {
        LocalDateTime now = LocalDateTime.now();
        Money premium = Money.of(new BigDecimal("1000.00"), "CNY");
        return new PolicyCreatedEvent(POLICY_ID, new PolicyNo("P202608120001"), PolicyForm.INDIVIDUAL,
                "PRODUCT_001", null, null, null, "BIZ_001", null,
                PolicyPeriod.of(now.minusMinutes(1), now.plusYears(1), 0, 0), premium, null, List.of(), null,
                CollectionInfo.initial(PremiumCollectionMode.ONLINE, premium, now), null,
                new PolicyStatus(PolicyStatusCode.NOT_EFFECTIVE, now, "创建", "SYSTEM"), null, null,
                TENANT_ID);
    }
}
