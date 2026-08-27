package com.titanium.policy.aggregate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.policy.PolicyLineStatus;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.command.ConvertProposalToInsuranceCommand;
import com.titanium.policy.entity.insurance.InsuranceLine;
import com.titanium.policy.exception.PolicyBusinessRuleException;

/**
 * 投保单创建不变量回归测试。
 */
class InsuranceCreationValidationTest {

    @Test
    void rejectsCreationWithoutStructuredInsuranceLinesBeforeWritingEvent() {
        new AggregateTestFixture<>(Insurance.class)
                .givenNoPriorActivity()
                .when(command(List.of()))
                .expectException(PolicyBusinessRuleException.class)
                .expectNoEvents();
    }

    @Test
    void rejectsCreationWhenLegacyProductCodeIsUsedAsProductId() {
        InsuranceLine incompleteLine = new InsuranceLine("LINE_001", 1, ProductCategory.MAIN, null, null,
                "PRODUCT_CODE_001", null, null, Money.of(new BigDecimal("500000"), "CNY"),
                Money.of(new BigDecimal("1000"), "CNY"), null, null, List.of(), null, null,
                PolicyLineStatus.UNDERWRITING);

        new AggregateTestFixture<>(Insurance.class)
                .givenNoPriorActivity()
                .when(command(List.of(incompleteLine)))
                .expectException(PolicyBusinessRuleException.class)
                .expectNoEvents();
    }

    private ConvertProposalToInsuranceCommand command(List<InsuranceLine> lines) {
        LocalDateTime periodStart = LocalDateTime.now();
        return ConvertProposalToInsuranceCommand.builder()
                .insuranceId("INSURANCE_001")
                .insuranceNo("INS_001")
                .proposalId("PROPOSAL_001")
                .policyForm(PolicyForm.INDIVIDUAL)
                .applicantId("CUSTOMER_001")
                .insuredCount(1)
                .exactPremium(Money.of(new BigDecimal("1000"), "CNY"))
                .insurancePeriodStart(periodStart)
                .insurancePeriodEnd(periodStart.plusYears(1))
                .insuranceLines(lines)
                .tenantId("TENANT_001")
                .build();
    }
}
