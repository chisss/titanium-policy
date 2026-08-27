package com.titanium.policy.aggregate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.metadata.enums.product.ProductEnum.SalesChannel;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.command.SubmitProposalCommand;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.entity.proposal.ProposalLine;
import com.titanium.policy.event.proposal.ProposalCreatedEvent;
import com.titanium.policy.event.proposal.ProposalSubmittedEvent;

/**
 * 统一出单参与方初始化 Proposal 聚合的回归测试。
 */
class ProposalUnifiedIssuanceTest {

    @Test
    void historicalCreatedEventDerivesInsuranceTypeFromUniqueMainLine() {
        Proposal proposal = new Proposal();

        proposal.on(createdEventWithInsuranceTypes(null, InsuranceProductType.TERM_LIFE));

        assertEquals(InsuranceProductType.TERM_LIFE, proposal.getInsuranceType());
    }

    @Test
    void historicalCreatedEventKeepsExplicitTopLevelInsuranceType() {
        Proposal proposal = new Proposal();

        proposal.on(createdEventWithInsuranceTypes(InsuranceProductType.WHOLE_LIFE,
                InsuranceProductType.TERM_LIFE));

        assertEquals(InsuranceProductType.WHOLE_LIFE, proposal.getInsuranceType());
    }

    @Test
    void createdEventInitializesApplicantAndSubjectSoAutomaticSubmitSucceeds() {
        String proposalId = "PROPOSAL_001";
        String tenantId = "TENANT_001";
        LocalDateTime createdAt = LocalDateTime.now();
        InsuredPartyList.HolderInfo holder = new InsuredPartyList.HolderInfo("CUSTOMER_HOLDER", "HOLDER_001", null,
                null, null, null);
        InsuredPartyList.InsuredInfo insured = new InsuredPartyList.InsuredInfo("CUSTOMER_INSURED", "INSURED_001",
                "李四", null, null, 35, null, null);
        InsuredPartyList parties = new InsuredPartyList("PARTIES_001", holder, List.of(insured), List.of());
        ProposalCreatedEvent created = new ProposalCreatedEvent(proposalId, "PRP_001", PolicyForm.INDIVIDUAL,
                SalesChannel.ONLINE, "CUSTOMER_HOLDER", new BigDecimal("500000"), new BigDecimal("1000"), createdAt,
                createdAt.plusYears(1), "P001", List.of(), null, "BIZ_001", null, createdAt, tenantId, parties, null,
                null, null, 0);

        new AggregateTestFixture<>(Proposal.class)
                .registerIgnoredField(Proposal.class, "status")
                .given(created)
                .when(new SubmitProposalCommand(proposalId, "统一出单自动提交意向单", tenantId))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(
                                org.axonframework.test.matchers.Matchers.<ProposalSubmittedEvent>predicate(event ->
                                        event instanceof ProposalSubmittedEvent submitted
                                                && proposalId.equals(submitted.proposalId())
                                                && tenantId.equals(submitted.tenantId())),
                                org.axonframework.test.matchers.Matchers.andNoMore())));
    }

    private ProposalCreatedEvent createdEventWithInsuranceTypes(InsuranceProductType topLevelType,
                                                                  InsuranceProductType mainLineType) {
        LocalDateTime createdAt = LocalDateTime.now();
        ProposalLine main = new ProposalLine("LINE_001", 1, ProductCategory.MAIN, null, "PRODUCT_001", "P001",
                mainLineType, Money.of(new BigDecimal("500000"), "CNY"), null);
        return new ProposalCreatedEvent("PROPOSAL_001", "PRP_001", PolicyForm.INDIVIDUAL, SalesChannel.ONLINE,
                "CUSTOMER_HOLDER", new BigDecimal("500000"), null, createdAt, createdAt.plusYears(20), "P001",
                List.of(main), topLevelType, "BIZ_001", null, createdAt, "TENANT_001");
    }
}
