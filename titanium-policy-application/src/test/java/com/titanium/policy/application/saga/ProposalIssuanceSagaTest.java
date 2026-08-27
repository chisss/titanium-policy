package com.titanium.policy.application.saga;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.test.matchers.Matchers;
import org.axonframework.test.saga.SagaTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.titanium.metadata.enums.billing.PremiumCollectionMode;
import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.insurance.SubjectType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.PaymentFrequency;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.metadata.enums.product.ProductEnum.SalesChannel;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.application.support.TestPolicyNoGenerator;
import com.titanium.policy.command.ConvertProposalToInsuranceCommand;
import com.titanium.policy.command.SubmitUnderwritingCommand;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.entity.proposal.ProposalLine;
import com.titanium.policy.event.proposal.ProposalConvertedEvent;
import com.titanium.policy.event.proposal.ProposalCreatedEvent;
import com.titanium.policy.valueobject.policy.ChannelInfo;
import com.titanium.policy.valueobject.policy.LineCoveragePeriod;
import com.titanium.policy.valueobject.policy.LinePaymentTerms;

/**
 * 三步出单 Proposal Saga 的关联与字段透传测试。
 */
class ProposalIssuanceSagaTest {

    private static final String PROPOSAL_ID = "PROPOSAL_001";
    private static final String TENANT_ID = "TENANT_001";
    private static final LocalDateTime PERIOD_START = LocalDateTime.of(2026, 9, 1, 0, 0);
    private static final LocalDateTime PERIOD_END = LocalDateTime.of(2046, 9, 1, 0, 0);

    private SagaTestFixture<ProposalIssuanceSaga> fixture;

    @BeforeEach
    void setUp() {
        fixture = new SagaTestFixture<>(ProposalIssuanceSaga.class);
        fixture.registerCommandGateway(CommandGateway.class);
        fixture.registerResource(new TestPolicyNoGenerator());
    }

    @Test
    void proposalCreatedStartsSagaAndAssociatesProposalId() {
        fixture.givenNoPriorActivity()
                .whenPublishingA(proposalCreatedEvent())
                .expectActiveSagas(1)
                .expectAssociationWith("proposalId", PROPOSAL_ID)
                .expectNoDispatchedCommands();
    }

    @Test
    void convertedProposalKeepsIntendedPremiumOutOfActualPremiumThenSubmitsUnderwriting() {
        AtomicReference<String> insuranceId = new AtomicReference<>();

        fixture.givenAggregate(PROPOSAL_ID).published(proposalCreatedEvent())
                .whenAggregate(PROPOSAL_ID).publishes(new ProposalConvertedEvent(PROPOSAL_ID, "自动转换",
                        LocalDateTime.now(), TENANT_ID))
                .expectActiveSagas(0)
                .expectDispatchedCommandsMatching(Matchers.payloadsMatching(Matchers.exactSequenceOf(
                        Matchers.<Object>predicate(command -> {
                            if (!(command instanceof ConvertProposalToInsuranceCommand create)) {
                                return false;
                            }
                            insuranceId.set(create.insuranceId());
                            return PROPOSAL_ID.equals(create.proposalId())
                                    && TENANT_ID.equals(create.tenantId())
                                    && "BIZ_001".equals(create.bizNo())
                                    && "PACKAGE_001".equals(create.marketPackageId())
                                    && InsuranceProductType.TERM_LIFE == create.insuranceType()
                                    && parties().equals(create.insuredPartyList())
                                    && PremiumCollectionMode.ONLINE == create.collectionMode()
                                    && channelInfo().equals(create.channelInfo())
                                    && new BigDecimal("500000").compareTo(create.sumInsured()) == 0
                                    && PaymentFrequency.ANNUAL.getCode().equals(create.paymentMode())
                                    && create.premiumPaymentYears() == 20
                                    && create.insuredCount() == 1
                                    && create.exactPremium() == null
                                    && create.insuranceLines().size() == 1
                                    && "P001".equals(create.insuranceLines().get(0).productCode())
                                    && Money.of(new BigDecimal("500000"), "CNY")
                                            .equals(create.insuranceLines().get(0).sumInsured())
                                    && LinePaymentTerms.annual(20)
                                            .equals(create.insuranceLines().get(0).paymentTerms())
                                    && LineCoveragePeriod.fixedTerm(PERIOD_START, PERIOD_END, null, null)
                                            .equals(create.insuranceLines().get(0).coveragePeriod())
                                    && create.insuranceLines().get(0).insuredSubjects().size() == 1
                                    && SubjectType.PERSON == create.insuranceLines().get(0).insuredSubjects().get(0)
                                            .subjectType()
                                    && "CUSTOMER_INSURED".equals(create.insuranceLines().get(0)
                                            .insuredSubjects().get(0).customerId())
                                    && "李四".equals(create.insuranceLines().get(0).insuredSubjects().get(0)
                                            .subjectName())
                                    && Integer.valueOf(35).equals(create.insuranceLines().get(0)
                                            .insuredSubjects().get(0).attributeAsInt("age"))
                                    && create.insuranceLines().get(0).premium() == null;
                        }),
                        Matchers.<Object>predicate(command ->
                                command instanceof SubmitUnderwritingCommand submit
                                        && insuranceId.get() != null
                                        && insuranceId.get().equals(submit.insuranceId())
                                        && TENANT_ID.equals(submit.tenantId())),
                        Matchers.andNoMore())));
    }

    @Test
    void convertedProposalKeepsExplicitTopLevelInsuranceType() {
        fixture.givenAggregate(PROPOSAL_ID)
                .published(proposalCreatedEvent(InsuranceProductType.WHOLE_LIFE))
                .whenAggregate(PROPOSAL_ID).publishes(new ProposalConvertedEvent(PROPOSAL_ID, "自动转换",
                        LocalDateTime.now(), TENANT_ID))
                .expectDispatchedCommandsMatching(Matchers.payloadsMatching(Matchers.exactSequenceOf(
                        Matchers.<Object>predicate(command -> command instanceof ConvertProposalToInsuranceCommand create
                                && InsuranceProductType.WHOLE_LIFE == create.insuranceType()),
                        Matchers.<Object>predicate(command -> command instanceof SubmitUnderwritingCommand),
                        Matchers.andNoMore())));
    }

    @Test
    void historicalSingleLineProposalDoesNotPromoteIntendedPremiumToActualPremium() {
        LocalDateTime periodStart = LocalDateTime.of(2025, 9, 1, 0, 0);
        LocalDateTime periodEnd = LocalDateTime.of(2026, 9, 1, 0, 0);
        ProposalCreatedEvent historical = new ProposalCreatedEvent(PROPOSAL_ID, "PRP_001", PolicyForm.INDIVIDUAL,
                SalesChannel.ONLINE, "CUSTOMER_HOLDER", new BigDecimal("500000"), new BigDecimal("1000"),
                periodStart, periodEnd, "P001", null, null, "BIZ_001", null,
                LocalDateTime.now(), TENANT_ID);

        fixture.givenAggregate(PROPOSAL_ID).published(historical)
                .whenAggregate(PROPOSAL_ID).publishes(new ProposalConvertedEvent(PROPOSAL_ID, "自动转换",
                        LocalDateTime.now(), TENANT_ID))
                .expectDispatchedCommandsMatching(Matchers.payloadsMatching(Matchers.exactSequenceOf(
                        Matchers.<Object>predicate(command -> command instanceof ConvertProposalToInsuranceCommand create
                                && create.insuranceLines().size() == 1
                                && create.insuranceLines().get(0).productId() == null
                                && "P001".equals(create.insuranceLines().get(0).productCode())
                                && Money.of(new BigDecimal("500000"), "CNY")
                                        .equals(create.insuranceLines().get(0).sumInsured())
                                && LineCoveragePeriod.fixedTerm(periodStart, periodEnd, null, null)
                                        .equals(create.insuranceLines().get(0).coveragePeriod())
                                && create.exactPremium() == null
                                && create.insuranceLines().get(0).premium() == null),
                        Matchers.<Object>predicate(command -> command instanceof SubmitUnderwritingCommand),
                        Matchers.andNoMore())));
    }

    @Test
    void jacksonPersistenceKeepsSagaBusinessState() throws Exception {
        ProposalIssuanceSaga saga = new ProposalIssuanceSaga();
        saga.on(proposalCreatedEvent());
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        String serialized = objectMapper.writeValueAsString(saga);
        ProposalIssuanceSaga restored = objectMapper.readValue(serialized, ProposalIssuanceSaga.class);
        String restoredJson = objectMapper.writeValueAsString(restored);

        assertTrue(serialized.contains("\"tenantId\":\"TENANT_001\""));
        assertTrue(serialized.contains("\"bizNo\":\"BIZ_001\""));
        assertTrue(restoredJson.contains("\"tenantId\":\"TENANT_001\""));
    }

    private ProposalCreatedEvent proposalCreatedEvent() {
        return proposalCreatedEvent(null);
    }

    private ProposalCreatedEvent proposalCreatedEvent(InsuranceProductType topLevelType) {
        ProposalLine line = new ProposalLine("LINE_001", 1, ProductCategory.MAIN, null, "PRODUCT_001", "P001",
                InsuranceProductType.TERM_LIFE, Money.of(new BigDecimal("500000"), "CNY"),
                Money.of(new BigDecimal("1000"), "CNY"));
        return new ProposalCreatedEvent(PROPOSAL_ID, "PRP_001", PolicyForm.INDIVIDUAL, SalesChannel.ONLINE,
                "CUSTOMER_HOLDER", new BigDecimal("500000"), new BigDecimal("1000"), PERIOD_START,
                PERIOD_END, "P001", List.of(line), topLevelType, "BIZ_001", "PACKAGE_001",
                LocalDateTime.now(), TENANT_ID, parties(), PremiumCollectionMode.ONLINE, channelInfo(),
                PaymentFrequency.ANNUAL.getCode(), 20);
    }

    private InsuredPartyList parties() {
        InsuredPartyList.HolderInfo holder = new InsuredPartyList.HolderInfo("CUSTOMER_HOLDER", "HOLDER_001", "张三",
                null, null, null);
        InsuredPartyList.InsuredInfo insured = new InsuredPartyList.InsuredInfo("CUSTOMER_INSURED", "INSURED_001",
                "李四", null, null, 35, null, null);
        return new InsuredPartyList("PARTIES_001", holder, List.of(insured), List.of());
    }

    private ChannelInfo channelInfo() {
        return new ChannelInfo("CHANNEL_001", null, SalesChannel.ONLINE, "AGENT_001", null);
    }
}
