package com.titanium.policy.application.saga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

import com.titanium.common.context.RequestContextHolder;
import com.titanium.metadata.enums.billing.PremiumCollectionMode;
import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.insurance.SubjectType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.PaymentFrequency;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.metadata.enums.product.ProductEnum.SalesChannel;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.application.orchestration.issuance.service.ProductSumInsuredTiersLoader;
import com.titanium.policy.application.support.TestPolicyNoGenerator;
import com.titanium.policy.command.ConvertProposalToInsuranceCommand;
import com.titanium.policy.command.SubmitUnderwritingCommand;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.entity.proposal.ProposalLine;
import com.titanium.policy.event.proposal.ProposalConvertedEvent;
import com.titanium.policy.event.proposal.ProposalCreatedEvent;
import com.titanium.policy.port.product.ProductServicePort;
import com.titanium.policy.valueobject.policy.ChannelInfo;
import com.titanium.policy.valueobject.policy.LineCoveragePeriod;
import com.titanium.policy.valueobject.policy.LinePaymentTerms;
import com.titanium.policy.valueobject.product.ProductSumInsuredTiers;

/**
 * 三步出单 Proposal Saga 的关联与字段透传测试。
 */
class ProposalIssuanceSagaTest {

    private static final String PROPOSAL_ID = "PROPOSAL_001";
    private static final String TENANT_ID = "TENANT_001";
    private static final LocalDateTime PERIOD_START = LocalDateTime.of(2026, 9, 1, 0, 0);
    private static final LocalDateTime PERIOD_END = LocalDateTime.of(2046, 9, 1, 0, 0);

    private SagaTestFixture<ProposalIssuanceSaga> fixture;

    private ProductServicePort productServicePort;

    /** 段级保额档次取数被调用瞬间的线程上下文租户（g02-06 回归守卫的证据位） */
    private String tenantIdOnProductCall;

    @BeforeEach
    void setUp() {
        fixture = new SagaTestFixture<>(ProposalIssuanceSaga.class);
        fixture.registerCommandGateway(CommandGateway.class);
        fixture.registerResource(new TestPolicyNoGenerator());
        // G02/AC-09：档次配置取数器与 IssuanceOrchestrator 共用同一组件，同为 transient 字段注入
        productServicePort = mock(ProductServicePort.class);
        when(productServicePort.getCoverageSumInsuredConfigs(any()))
                .thenAnswer(invocation -> {
                    // 采集调用瞬间的线程上下文租户——Saga 是否在租户作用域内发起出站调用的直接证据
                    tenantIdOnProductCall = RequestContextHolder.getTenantId();
                    return new ProductSumInsuredTiers("PRODUCT_001", List.of());
                });
        fixture.registerResource(new ProductSumInsuredTiersLoader(productServicePort));
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
                                    && create.insuranceLines().get(0).premium() == null
                                    // G02/AC-09：档次配置经 Saga 取回并作为命令分量交给聚合
                                    && create.sumInsuredTiers() != null
                                    && create.sumInsuredTiers().size() == 1
                                    && "PRODUCT_001".equals(create.sumInsuredTiers().get(0).productId());
                        }),
                        Matchers.<Object>predicate(command ->
                                command instanceof SubmitUnderwritingCommand submit
                                        && insuranceId.get() != null
                                        && insuranceId.get().equals(submit.insuranceId())
                                        && TENANT_ID.equals(submit.tenantId())),
                        Matchers.andNoMore())));
    }

    /**
     * 🔴 回归守卫（2026-09-27 g02-06 真机缺陷）：Saga 步骤必须在**事件携带的租户作用域**内执行
     * <p>
     * 本类处理器运行在 Axon tracking 线程上（无请求上下文），而 {@code ProductSumInsuredTiersLoader}
     * 的段级保额档次取数是<b>出站 Feign</b>：无租户上下文时出站拦截器静默不加 {@code X-Tenant-Id}，
     * 对端契约入口 400，且取数器「远程失败不兜底」会把异常抛给 Saga ⇒ 意向单转投保单链路中断。
     * 故断言两点：出站调用发起时上下文已带事件租户；步骤结束后上下文还原，不泄漏到线程池复用线程。
     * </p>
     */
    @Test
    void convertedProposalRunsTierLookupWithinEventTenantScope() {
        String tenantBefore = RequestContextHolder.getTenantId();

        fixture.givenAggregate(PROPOSAL_ID).published(proposalCreatedEvent())
                .whenAggregate(PROPOSAL_ID).publishes(new ProposalConvertedEvent(PROPOSAL_ID, "自动转换",
                        LocalDateTime.now(), TENANT_ID))
                .expectActiveSagas(0);

        assertEquals(TENANT_ID, tenantIdOnProductCall,
                "段级保额档次取数（出站 Feign）被调用时线程上下文必须已建立事件租户作用域");
        assertEquals(tenantBefore, RequestContextHolder.getTenantId(),
                "Saga 步骤结束后必须还原线程上下文（runAsTenant 的 finally 语义），不得泄漏到复用线程");
    }

    /**
     * 🔴 G11/g11-04 D4 / AC-05：多主险段**各自保留**缴费条款——交强年缴 1 年 + 商业月缴 12 年，
     * 转投保单时逐段解析，不得共用同一份单据级摘要（旧实现全段串用，串用结果还随段顺序变化）。
     * <p>
     * 段级缺失回退单据级的行为由上方既有用例（10 参 ProposalLine，段级缴费为空 ⇒ annual(20)）覆盖。
     * </p>
     */
    @Test
    void keepsPerMainLinePaymentTermsForMultiMainProposal() {
        ProposalLine compulsory = proposalLine("LINE_001", 1, "PRODUCT_CMP", "P_CMP",
                new BigDecimal("500000"), new BigDecimal("950"), PaymentFrequency.ANNUAL, 1);
        ProposalLine commercial = proposalLine("LINE_002", 2, "PRODUCT_CMR", "P_CMR",
                new BigDecimal("1000000"), new BigDecimal("3000"), PaymentFrequency.MONTHLY, 12);

        fixture.givenAggregate(PROPOSAL_ID).published(proposalCreatedEvent(List.of(compulsory, commercial)))
                .whenAggregate(PROPOSAL_ID).publishes(new ProposalConvertedEvent(PROPOSAL_ID, "自动转换",
                        LocalDateTime.now(), TENANT_ID))
                .expectDispatchedCommandsMatching(Matchers.payloadsMatching(Matchers.exactSequenceOf(
                        Matchers.<Object>predicate(command -> command instanceof ConvertProposalToInsuranceCommand create
                                && create.insuranceLines().size() == 2
                                && new LinePaymentTerms(PaymentFrequency.ANNUAL, 1)
                                        .equals(create.insuranceLines().get(0).paymentTerms())
                                && new LinePaymentTerms(PaymentFrequency.MONTHLY, 12)
                                        .equals(create.insuranceLines().get(1).paymentTerms())
                                // 单据级摘要（事件携带的 paymentMode/premiumPaymentYears）仍透传，不因逐段解析改变
                                && PaymentFrequency.ANNUAL.getCode().equals(create.paymentMode())
                                && create.premiumPaymentYears() == 20),
                        Matchers.<Object>predicate(command -> command instanceof SubmitUnderwritingCommand),
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
        return proposalCreatedEvent((InsuranceProductType) null);
    }

    private ProposalCreatedEvent proposalCreatedEvent(InsuranceProductType topLevelType) {
        ProposalLine line = new ProposalLine("LINE_001", 1, ProductCategory.MAIN, null, "PRODUCT_001", "P001",
                InsuranceProductType.TERM_LIFE, Money.of(new BigDecimal("500000"), "CNY"),
                Money.of(new BigDecimal("1000"), "CNY"));
        return proposalCreatedEvent(List.of(line), topLevelType);
    }

    private ProposalCreatedEvent proposalCreatedEvent(List<ProposalLine> lines) {
        return proposalCreatedEvent(lines, null);
    }

    private ProposalCreatedEvent proposalCreatedEvent(List<ProposalLine> lines, InsuranceProductType topLevelType) {
        return new ProposalCreatedEvent(PROPOSAL_ID, "PRP_001", PolicyForm.INDIVIDUAL, SalesChannel.ONLINE,
                "CUSTOMER_HOLDER", new BigDecimal("500000"), new BigDecimal("1000"), PERIOD_START,
                PERIOD_END, "P001", lines, topLevelType, "BIZ_001", "PACKAGE_001",
                LocalDateTime.now(), TENANT_ID, parties(), PremiumCollectionMode.ONLINE, channelInfo(),
                PaymentFrequency.ANNUAL.getCode(), 20);
    }

    /** 带段级缴费条件的主险意向段（G11/g11-04 D4 逐段缴费用例）。 */
    private ProposalLine proposalLine(String lineId, int lineNo, String productId, String productCode,
                                      BigDecimal sumInsured, BigDecimal premium,
                                      PaymentFrequency frequency, Integer paymentYears) {
        return new ProposalLine(lineId, lineNo, ProductCategory.MAIN, null, productId, productCode, null,
                InsuranceProductType.AUTO, Money.of(sumInsured, "CNY"), Money.of(premium, "CNY"),
                frequency, paymentYears);
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
