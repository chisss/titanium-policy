package com.titanium.policy.application.saga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.test.matchers.Matchers;
import org.axonframework.test.saga.SagaTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.common.context.RequestContextHolder;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.PaymentFrequency;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;
import com.titanium.metadata.errorcode.SystemErrorCode;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.application.orchestration.issuance.assembler.InsuranceLineAssembler;
import com.titanium.policy.application.orchestration.issuance.assembler.PolicyProductAssembler;
import com.titanium.policy.application.orchestration.issuance.orchestrator.PremiumCollectionOrchestrator;
import com.titanium.policy.application.orchestration.issuance.orchestrator.PremiumScheduleOrchestrator;
import com.titanium.policy.application.orchestration.issuance.service.InsuranceLinePremiumConfirmationService;
import com.titanium.policy.application.orchestration.issuance.validator.ConfirmedPremiumRequestValidator;
import com.titanium.policy.application.support.TestPolicyNoGenerator;
import com.titanium.policy.command.CreatePolicyCommand;
import com.titanium.policy.command.ReceiveUnderwritingResultCommand;
import com.titanium.policy.command.TriggerIssuanceCommand;
import com.titanium.policy.common.constant.PolicyConstants.LineExtendDataKey;
import com.titanium.policy.event.insurance.InsuranceCreatedEvent;
import com.titanium.policy.event.insurance.InsuranceIssuedEvent;
import com.titanium.policy.event.insurance.InsuranceSubmittedForUnderwritingEvent;
import com.titanium.policy.event.insurance.UnderwritingResultReceivedEvent;
import com.titanium.policy.exception.RemoteDependencyUnavailableException;
import com.titanium.policy.port.billing.BillingServicePort;
import com.titanium.policy.port.billing.ConfirmedPremiumPricingPort;
import com.titanium.policy.port.clause.ClauseServicePort;
import com.titanium.policy.port.investment.InvestmentAccountPort;
import com.titanium.policy.port.product.ProductServicePort;
import com.titanium.policy.port.underwriting.UnderwritingDecisionGateway;
import com.titanium.policy.service.impl.PolicyIssuanceDomainServiceImpl;
import com.titanium.policy.valueobject.billing.BillingResult;
import com.titanium.policy.valueobject.billing.PremiumBillRequest;
import com.titanium.policy.valueobject.billing.PremiumScheduleRequest;
import com.titanium.policy.valueobject.billing.UnpaidBill;
import com.titanium.policy.valueobject.insurance.UnderwritingDecisionRequest;
import com.titanium.policy.valueobject.insurance.UnderwritingResult;
import com.titanium.policy.valueobject.issuance.HealthDeclarationInput;
import com.titanium.policy.valueobject.policy.CollectionResult;
import com.titanium.policy.valueobject.policy.LineCoveragePeriod;
import com.titanium.policy.valueobject.policy.LinePaymentTerms;
import com.titanium.policy.valueobject.pricing.ConfirmedPremiumRequest;
import com.titanium.policy.valueobject.pricing.ConfirmedPremiumResult;
import com.titanium.policy.valueobject.product.ProductBasicInfo;
import com.titanium.policy.valueobject.product.ProductClauseRef;
import com.titanium.policy.valueobject.product.ProductIssueRules;
import com.titanium.policy.valueobject.product.ProductSumInsuredTiers;

/**
 * 投保出单 Saga 全链路编排测试
 * <p>
 * 验证投保 → 核保 → 承保 → 出单 各步骤的事件驱动流转：
 * <ol>
 *     <li>投保单创建启动 Saga 并建立关联；</li>
 *     <li>提交核保经核保网关获取结论并回写结果命令；</li>
 *     <li>核保通过触发承保出单命令；核保拒绝结束 Saga；</li>
 *     <li>承保出单创建正式保单并结束 Saga。</li>
 * </ol>
 * 核保网关以可控桩对象替身，保证测试确定性、不依赖核保域真实服务。
 * </p>
 */
public class IssuanceSagaTest {

    private static final String INSURANCE_ID = "INS_001";
    private static final String PRODUCT_ID = "PRODUCT_ID_001";
    private static final String PRODUCT_CODE = "PRODUCT_CODE_001";
    private static final String TENANT_ID    = "TENANT_001";

    private SagaTestFixture<IssuanceSaga> fixture;
    private StubUnderwritingDecisionGateway underwritingGateway;
    private StubBillingServicePort billingServicePort;
    private StubConfirmedPremiumPricingPort confirmedPremiumPricingPort;
    private PremiumCollectionOrchestrator premiumCollectionOrchestrator;
    private StubProductServicePort productServicePort;
    private StubInvestmentAccountPort investmentAccountPort;

    @BeforeEach
    public void setUp() {
        fixture = new SagaTestFixture<>(IssuanceSaga.class);
        underwritingGateway = new StubUnderwritingDecisionGateway();
        fixture.registerCommandGateway(CommandGateway.class);
        fixture.registerResource(underwritingGateway);
        fixture.registerResource(new TestPolicyNoGenerator());
        fixture.registerResource(new PolicyIssuanceDomainServiceImpl());
        confirmedPremiumPricingPort = new StubConfirmedPremiumPricingPort();
        fixture.registerResource(new InsuranceLinePremiumConfirmationService(confirmedPremiumPricingPort,
                new ConfirmedPremiumRequestValidator(),
                new com.titanium.policy.application.orchestration.issuance.assembler.ConfirmedPremiumRequestAssembler()));
        // 险种段化后 Saga 新增依赖：产品配置（等待期/犹豫期）、条款责任装配、计费开单、投资开户
        productServicePort = new StubProductServicePort();
        fixture.registerResource(productServicePort);
        fixture.registerResource(new StubClauseServicePort());
        billingServicePort = new StubBillingServicePort();
        fixture.registerResource(new PremiumScheduleOrchestrator(billingServicePort));
        investmentAccountPort = new StubInvestmentAccountPort();
        fixture.registerResource(investmentAccountPort);
        fixture.registerResource(new PolicyProductAssembler(productServicePort, new StubClauseServicePort(),
                new InsuranceLineAssembler(productServicePort)));
        premiumCollectionOrchestrator = mock(PremiumCollectionOrchestrator.class);
        when(premiumCollectionOrchestrator.collect(anyString(), anyString(), any(Money.class),
                isNull(), any(LocalDate.class), anyString(), anyList()))
                .thenReturn(CollectionResult.pending("BILL_001", "ACCOUNT_001", null, null));
        fixture.registerResource(premiumCollectionOrchestrator);
    }

    /**
     * 投保单创建 → 启动 Saga 并与 insuranceId 建立关联
     */
    @Test
    public void testInsuranceCreatedStartsSaga() {
        fixture.givenNoPriorActivity()
                .whenPublishingA(insuranceCreatedEvent())
                .expectActiveSagas(1)
                .expectAssociationWith("insuranceId", INSURANCE_ID)
                .expectNoDispatchedCommands();
    }

    /**
     * 提交核保 → 经核保网关获取结论，回写 ReceiveUnderwritingResultCommand
     */
    @Test
    public void testSubmitUnderwritingDispatchesReceiveResult() {
        UnderwritingResult approved = approvedResult();
        underwritingGateway.setResult(approved);

        fixture.givenAggregate(INSURANCE_ID).published(insuranceCreatedEvent())
                .whenAggregate(INSURANCE_ID).publishes(insuranceSubmittedEvent())
                .expectActiveSagas(1)
                .expectDispatchedCommands(
                        new ReceiveUnderwritingResultCommand(INSURANCE_ID, approved, TENANT_ID));

        assertEquals(new BigDecimal("500000"), underwritingGateway.lastRequest().sumInsured());
        assertEquals(new BigDecimal("1000.00"), underwritingGateway.lastRequest().premium());
    }

    /**
     * 🔴 G12/g12-01 AC-01 正向：段级 {@code extendData} 的健康告知必须随核保请求抵达网关
     * <p>
     * 此前该块在 {@code InsuranceLine} 处被丢弃、Saga 构造请求时末参恒为 null，核保域只能按
     * 「无告知」评分——本用例锁死「同一投保单带『既往病史 = 高血压』时核保决策入参可观察到差异」
     * 的「带」这一半。
     * </p>
     */
    @Test
    public void testHealthDeclarationFromLineExtendDataReachesUnderwritingRequest() {
        underwritingGateway.setResult(approvedResult());
        Map<String, Object> extendData = Map.of(LineExtendDataKey.HEALTH_DECLARATION, Map.of(
                LineExtendDataKey.HEALTH_DECLARATION_MEDICAL_HISTORY, List.of("高血压"),
                LineExtendDataKey.HEALTH_DECLARATION_FAMILY_HISTORY, List.of("糖尿病家族史"),
                LineExtendDataKey.HEALTH_DECLARATION_SMOKING, true,
                LineExtendDataKey.HEALTH_DECLARATION_HEIGHT_CM, new BigDecimal("175"),
                LineExtendDataKey.HEALTH_DECLARATION_WEIGHT_KG, new BigDecimal("80")));

        fixture.givenAggregate(INSURANCE_ID).published(insuranceCreatedEvent(mainInsuranceLine(extendData)))
                .whenAggregate(INSURANCE_ID).publishes(insuranceSubmittedEvent())
                .expectActiveSagas(1);

        HealthDeclarationInput declaration = underwritingGateway.lastRequest().healthDeclaration();
        assertNotNull(declaration, "段级 extendData 已携带完整健康告知，核保请求不得为空");
        assertEquals(List.of("高血压"), declaration.medicalHistory());
        assertEquals(List.of("糖尿病家族史"), declaration.familyHistory());
        assertTrue(declaration.smoking());
        assertEquals(new BigDecimal("175"), declaration.heightCm());
        assertEquals(new BigDecimal("80"), declaration.weightKg());
    }

    /**
     * 🔴 G12/g12-01 AC-01 反向：段上无告知块时核保请求的健康告知必须为 null（不代填默认值）
     * <p>
     * 「未提供」与「提供了低风险告知」必须在核保入参层可区分：若此处代填一个「无病史、不吸烟」
     * 的空壳，核保域会把「没告知」读成「告知了低风险」，风险方向被反转。
     * </p>
     */
    @Test
    public void testHealthDeclarationStaysNullWhenLineCarriesNoExtendData() {
        underwritingGateway.setResult(approvedResult());

        fixture.givenAggregate(INSURANCE_ID).published(insuranceCreatedEvent())
                .whenAggregate(INSURANCE_ID).publishes(insuranceSubmittedEvent())
                .expectActiveSagas(1);

        assertNull(underwritingGateway.lastRequest().healthDeclaration(),
                "段上无健康告知块时不得代填默认告知");
    }

    /**
     * 🔴 回归守卫（2026-09-27 g02-06 真机缺陷）：Saga 步骤必须在**事件携带的租户作用域**内执行
     * <p>
     * Saga 处理器运行在 Axon tracking 线程上，该线程没有请求上下文（{@code ThreadLocal} 不跨线程传播）；
     * 而出站 Feign 拦截器是「上下文有租户才注入 {@code X-Tenant-Id}」策略 ⇒ 缺头时对端契约入口返回
     * 400 {@code TENANT_HEADER_MISSING} ⇒ Saga 进退避重试并**阻塞整个 tracking segment**（真机表现为
     * 「出单第 4 步调产品试算失败、后续新单完全不推进」）。故断言两点：网关被调用时上下文中已有事件
     * 租户；步骤结束后上下文还原，不把租户泄漏到线程池复用线程。
     * </p>
     */
    @Test
    public void testSagaStepRunsWithinEventTenantScope() {
        underwritingGateway.setResult(approvedResult());
        String tenantBefore = RequestContextHolder.getTenantId();

        fixture.givenAggregate(INSURANCE_ID).published(insuranceCreatedEvent())
                .whenAggregate(INSURANCE_ID).publishes(insuranceSubmittedEvent())
                .expectActiveSagas(1);

        assertEquals(TENANT_ID, underwritingGateway.tenantIdOnCall(),
                "核保网关被调用时线程上下文必须已建立事件租户作用域，否则出站 Feign 静默缺 X-Tenant-Id 头");
        assertEquals(tenantBefore, RequestContextHolder.getTenantId(),
                "Saga 步骤结束后必须还原线程上下文（runAsTenant 的 finally 语义），不得泄漏到复用线程");
    }

    /**
     * 核保通过 → 触发承保出单命令，Saga 保持活跃
     */
    @Test
    public void testUnderwritingApprovedTriggersIssuance() {
        fixture.givenAggregate(INSURANCE_ID).published(insuranceCreatedEvent())
                .whenAggregate(INSURANCE_ID).publishes(underwritingResultEvent(ConclusionType.ACCEPT))
                .expectActiveSagas(1)
                .expectDispatchedCommands(new TriggerIssuanceCommand(INSURANCE_ID, TENANT_ID));
    }

    /**
     * 核保拒绝 → 不触发承保，结束 Saga
     */
    @Test
    public void testUnderwritingRejectedEndsSaga() {
        fixture.givenAggregate(INSURANCE_ID).published(insuranceCreatedEvent())
                .whenAggregate(INSURANCE_ID).publishes(underwritingResultEvent(ConclusionType.REJECT))
                .expectActiveSagas(0)
                .expectNoDispatchedCommands();
    }

    /**
     * 核保暂缓 → 不触发承保，结束 Saga（待人工介入后另行驱动）
     */
    @Test
    public void testUnderwritingSuspendedEndsSaga() {
        fixture.givenAggregate(INSURANCE_ID).published(insuranceCreatedEvent())
                .whenAggregate(INSURANCE_ID).publishes(underwritingResultEvent(ConclusionType.POSTPONE))
                .expectActiveSagas(0)
                .expectNoDispatchedCommands();
    }

    /**
     * 承保出单 → 创建正式保单并结束 Saga
     * <p>policyId/policyNo 由 Saga 内部生成，故用谓词匹配关键字段而非全等比较。</p>
     */
    @Test
    public void testInsuranceIssuedCreatesPolicyAndEndsSaga() {
        AtomicReference<String> createdPolicyId = new AtomicReference<>();

        fixture.givenAggregate(INSURANCE_ID).published(insuranceCreatedEvent())
                .whenAggregate(INSURANCE_ID).publishes(insuranceIssuedEvent())
                .expectActiveSagas(0)
                .expectDispatchedCommandsMatching(Matchers.payloadsMatching(Matchers.exactSequenceOf(
                        Matchers.<CreatePolicyCommand>predicate(cmd -> {
                            if (!(cmd instanceof CreatePolicyCommand c)
                                    || !INSURANCE_ID.equals(c.insuranceId())
                                    || !TENANT_ID.equals(c.tenantId())
                                    || PolicyForm.INDIVIDUAL != c.policyForm()
                                    || !PRODUCT_ID.equals(c.productId())
                                    || c.policyId() == null
                                    || c.policyNo() == null) {
                                return false;
                            }
                            createdPolicyId.set(c.policyId());
                            return true;
                        }),
                        Matchers.andNoMore())));

        verify(premiumCollectionOrchestrator).collect(eq(createdPolicyId.get()), eq("HOLDER_001"),
                eq(Money.of(new BigDecimal("1000.00"), "CNY")), isNull(),
                eq(insuranceCreatedEvent().insurancePeriodStart().toLocalDate()), eq(TENANT_ID), anyList());
        assertEquals(List.of(PRODUCT_ID), confirmedPremiumPricingPort.requestedProductIds());
    }

    @Test
    public void testInsuranceIssuedKeepsStandardPremiumSeparateFromUnderwritingSurcharge() {
        UnderwritingResultReceivedEvent modified = new UnderwritingResultReceivedEvent(INSURANCE_ID, "UW_001",
                ConclusionType.MODIFY, "加费承保", "UW_USER_001", LocalDateTime.now(), "加费20%", TENANT_ID,
                new BigDecimal("0.20"));

        fixture.givenAggregate(INSURANCE_ID).published(insuranceCreatedEvent(), modified)
                .whenAggregate(INSURANCE_ID).publishes(insuranceIssuedEvent())
                .expectActiveSagas(0)
                .expectDispatchedCommandsMatching(Matchers.payloadsMatching(Matchers.exactSequenceOf(
                        Matchers.<CreatePolicyCommand>predicate(command ->
                                Money.of(new BigDecimal("1000.00"), "CNY").equals(command.standardPremium())
                                        && Money.of(new BigDecimal("1200.00"), "CNY").equals(command.premium())),
                        Matchers.andNoMore())));
    }

    /**
     * 期缴计划的总保费必须与保单全部承保险种段的应付总保费保持一致（主险 1000 + 附加险 200）。
     */
    @Test
    public void testPremiumScheduleUsesPolicyPayablePremium() {
        fixture.givenAggregate(INSURANCE_ID).published(insuranceCreatedEventWithRider())
                .whenAggregate(INSURANCE_ID).publishes(insuranceIssuedEvent())
                .expectActiveSagas(0);

        PremiumScheduleRequest schedule = billingServicePort.lastSchedule();
        assertEquals(20, schedule.totalPeriods());
        assertEquals(new BigDecimal("1200.00"), schedule.totalPremium());
        assertEquals(List.of(PRODUCT_ID, "RIDER_ID_001"), confirmedPremiumPricingPort.requestedProductIds());
    }

    /**
     * 期缴计划生成失败只进入补偿路径，不能回滚已经完成的保单创建。
     */
    @Test
    public void testPremiumScheduleFailureDoesNotBlockPolicyCreation() {
        billingServicePort.failSchedule(new RuntimeException("billing unavailable"));

        fixture.givenAggregate(INSURANCE_ID).published(insuranceCreatedEvent())
                .whenAggregate(INSURANCE_ID).publishes(insuranceIssuedEvent())
                .expectActiveSagas(0)
                .expectDispatchedCommandsMatching(Matchers.payloadsMatching(Matchers.exactSequenceOf(
                        Matchers.<CreatePolicyCommand>predicate(command -> INSURANCE_ID.equals(command.insuranceId())),
                        Matchers.andNoMore())));

        PremiumScheduleRequest attemptedSchedule = billingServicePort.lastSchedule();
        assertEquals(20, attemptedSchedule.totalPeriods());
        assertEquals(new BigDecimal("1000.00"), attemptedSchedule.totalPremium());
    }

    // ==================== G14/g14-04a 管理费费率来源 ====================

    /**
     * 投连保单出单：管理费费率取产品定价配置（{@code ProductServicePort.getManagementFeeRate}）并透传开户端口。
     * <p>
     * 桩值 0.0234 刻意区别于 policy 侧原硬编码兜底 0.015——若链路仍走兜底常量、或取数未接通而传 null，
     * 本断言即红（有区分力的判据）。
     * </p>
     */
    @Test
    public void testInvestmentLinkedIssuancePassesManagementFeeRateFromProduct() {
        fixture.givenAggregate(INSURANCE_ID).published(investmentLinkedInsuranceCreatedEvent())
                .whenAggregate(INSURANCE_ID).publishes(insuranceIssuedEvent())
                .expectActiveSagas(0);

        assertTrue(investmentAccountPort.openAccountCalled, "投连保单出单必须开立投资账户");
        assertEquals(PRODUCT_ID, productServicePort.lastManagementFeeRateProductId,
                "费率须按保单标的产品ID取数");
        assertEquals(new BigDecimal("0.0234"), investmentAccountPort.lastManagementFeeRate,
                "开户须透传取数所得费率，不得回退硬编码兜底");
    }

    /**
     * 费率取数失败（远程依赖不可用）：本期不建账户、待补偿，且不阻断出单。
     * <p>
     * 「无账户（可见的缺失）」优于「有账户但费率被静默折叠成 null」（后者每月计提静默跳过、事后无从对账）。
     * </p>
     */
    @Test
    public void testManagementFeeRateUnavailableSkipsAccountOpening() {
        productServicePort.failManagementFeeRate(new RemoteDependencyUnavailableException(
                SystemErrorCode.DEPENDENCY_SERVICE_ERROR, "产品服务不可用"));

        fixture.givenAggregate(INSURANCE_ID).published(investmentLinkedInsuranceCreatedEvent())
                .whenAggregate(INSURANCE_ID).publishes(insuranceIssuedEvent())
                .expectActiveSagas(0);

        assertFalse(investmentAccountPort.openAccountCalled,
                "取数失败不得以 null 顶替开户——应不建账户待补偿");
    }

    /**
     * 产品未配置管理费率（null）：合法形态，仍开立账户且原样透传 null（=不收费）。
     */
    @Test
    public void testUnconfiguredManagementFeeRateStillOpensAccount() {
        productServicePort.setManagementFeeRate(null);

        fixture.givenAggregate(INSURANCE_ID).published(investmentLinkedInsuranceCreatedEvent())
                .whenAggregate(INSURANCE_ID).publishes(insuranceIssuedEvent())
                .expectActiveSagas(0);

        assertTrue(investmentAccountPort.openAccountCalled, "未配置费率是合法形态，仍须开户");
        assertNull(investmentAccountPort.lastManagementFeeRate, "未配置须原样透传 null，不得以默认值兜底");
    }

    // ==================== 测试夹具数据 ====================

    private InsuranceCreatedEvent insuranceCreatedEvent() {
        return insuranceCreatedEvent(mainInsuranceLine());
    }

    private InsuranceCreatedEvent insuranceCreatedEvent(com.titanium.policy.entity.insurance.InsuranceLine mainLine) {
        return new InsuranceCreatedEvent(INSURANCE_ID, "INS_NO_001", null, PolicyForm.INDIVIDUAL, "HOLDER_001", 1,
                new BigDecimal("1000.00"), LocalDateTime.now(), LocalDateTime.now().plusYears(1),
                List.of(mainLine), 0, null, null, null, null, "BIZ_NO_001", null, LocalDateTime.now(),
                TENANT_ID, new BigDecimal("500000"), "ANNUAL", 20);
    }

    private InsuranceCreatedEvent insuranceCreatedEventWithRider() {
        InsuranceCreatedEvent event = insuranceCreatedEvent();
        return new InsuranceCreatedEvent(event.insuranceId(), event.insuranceNo(), event.proposalId(),
                event.policyForm(), event.holderId(), event.insuredCount(), new BigDecimal("1200.00"),
                event.insurancePeriodStart(), event.insurancePeriodEnd(),
                List.of(mainInsuranceLine().withPremium(null), riderInsuranceLine().withPremium(null)),
                event.underwritingPriority(),
                event.insuredPartyList(), event.insuranceType(), event.collectionMode(), event.channelInfo(),
                event.bizNo(), event.marketPackageId(), event.createTime(), event.tenantId(), event.sumInsured(),
                event.paymentMode(), event.premiumPaymentYears());
    }

    /** 投连（万能险）形态投保单事件：触发 Saga 的投连/万能分支（开户 + 费率透传） */
    private InsuranceCreatedEvent investmentLinkedInsuranceCreatedEvent() {
        InsuranceCreatedEvent event = insuranceCreatedEvent();
        return new InsuranceCreatedEvent(event.insuranceId(), event.insuranceNo(), event.proposalId(),
                PolicyForm.UNIVERSAL, event.holderId(), event.insuredCount(), new BigDecimal("1000.00"),
                event.insurancePeriodStart(), event.insurancePeriodEnd(), List.of(mainInsuranceLine()),
                event.underwritingPriority(),
                event.insuredPartyList(), event.insuranceType(), event.collectionMode(), event.channelInfo(),
                event.bizNo(), event.marketPackageId(), event.createTime(), event.tenantId(), event.sumInsured(),
                event.paymentMode(), event.premiumPaymentYears());
    }

    /** 主险段测试夹具（一单一险场景，段列表长度 1） */
    private com.titanium.policy.entity.insurance.InsuranceLine mainInsuranceLine() {
        return mainInsuranceLine(null);
    }

    /**
     * 主险段夹具（指定段级扩展数据，G12/g12-01 健康告知透传用例使用）
     */
    private com.titanium.policy.entity.insurance.InsuranceLine mainInsuranceLine(Map<String, Object> extendData) {
        return new com.titanium.policy.entity.insurance.InsuranceLine("LINE_001", 1,
                com.titanium.metadata.enums.product.ProductEnum.ProductCategory.MAIN, null, PRODUCT_ID,
                PRODUCT_CODE, "测试产品", "V1.0", null,
                com.titanium.metadata.valueobject.Money.of(new BigDecimal("500000"), "CNY"),
                com.titanium.metadata.valueobject.Money.of(new BigDecimal("1000.00"), "CNY"),
                LineCoveragePeriod.fixedTerm(LocalDateTime.now(), LocalDateTime.now().plusYears(1), null, null),
                new LinePaymentTerms(PaymentFrequency.ANNUAL, 20), List.of(personSubject()),
                null, null, com.titanium.metadata.enums.policy.PolicyLineStatus.UNDERWRITING, extendData);
    }

    private com.titanium.policy.entity.insurance.InsuranceLine riderInsuranceLine() {
        return new com.titanium.policy.entity.insurance.InsuranceLine("LINE_002", 2,
                com.titanium.metadata.enums.product.ProductEnum.ProductCategory.RIDER, "LINE_001", "RIDER_ID_001",
                "RIDER_CODE_001", "测试附加险", "V1.0", null,
                com.titanium.metadata.valueobject.Money.of(new BigDecimal("100000"), "CNY"),
                com.titanium.metadata.valueobject.Money.of(new BigDecimal("200.00"), "CNY"),
                LineCoveragePeriod.fixedTerm(LocalDateTime.now(), LocalDateTime.now().plusYears(1), null, null),
                new LinePaymentTerms(PaymentFrequency.ANNUAL, 1), List.of(personSubject()),
                null, null, com.titanium.metadata.enums.policy.PolicyLineStatus.UNDERWRITING, null);
    }

    private com.titanium.policy.entity.policy.InsuredSubject personSubject() {
        return com.titanium.policy.entity.policy.InsuredSubject.ofPerson(
                "SUBJECT_001", "CUSTOMER_001", "被保险人", Money.of(new BigDecimal("500000"), "CNY"),
                java.util.Map.of("age", 35, "gender", "M"));
    }

    private InsuranceSubmittedForUnderwritingEvent insuranceSubmittedEvent() {
        return new InsuranceSubmittedForUnderwritingEvent(INSURANCE_ID, "INS_NO_001", "HOLDER_001", 1,
                new BigDecimal("1000.00"), "CNY", LocalDateTime.now(), LocalDateTime.now().plusYears(1),
                List.of(PRODUCT_CODE), 0, PolicyForm.INDIVIDUAL, TENANT_ID);
    }

    private UnderwritingResultReceivedEvent underwritingResultEvent(ConclusionType resultCode) {
        return new UnderwritingResultReceivedEvent(INSURANCE_ID, "UW_001", resultCode, "核保意见", "UW_USER_001",
                LocalDateTime.now(), null, TENANT_ID, null);
    }

    private InsuranceIssuedEvent insuranceIssuedEvent() {
        return new InsuranceIssuedEvent(INSURANCE_ID, "INS_NO_001", LocalDateTime.now(), TENANT_ID);
    }

    private UnderwritingResult approvedResult() {
        return new UnderwritingResult("UW_001", ConclusionType.ACCEPT, "核保通过", "UW_USER_001",
                LocalDateTime.now(), null, null);
    }

    /**
     * 核保决策网关桩：返回可配置的固定结论，保证测试确定性
     */
    private static class StubUnderwritingDecisionGateway implements UnderwritingDecisionGateway {
        private UnderwritingResult result;
        private UnderwritingDecisionRequest lastRequest;
        /** 被调用瞬间的线程上下文租户——Saga 是否在租户作用域内发起出站调用的直接证据（g02-06 回归守卫） */
        private String tenantIdOnCall;

        void setResult(UnderwritingResult result) {
            this.result = result;
        }

        @Override
        public UnderwritingResult requestDecision(UnderwritingDecisionRequest request) {
            this.lastRequest = request;
            this.tenantIdOnCall = RequestContextHolder.getTenantId();
            return result;
        }

        UnderwritingDecisionRequest lastRequest() {
            return lastRequest;
        }

        String tenantIdOnCall() {
            return tenantIdOnCall;
        }
    }

    /**
     * 保费计算网关桩：返回固定保费，保证测试确定性（不依赖 billing 域）
     */
    private static class StubConfirmedPremiumPricingPort implements ConfirmedPremiumPricingPort {
        private final java.util.ArrayList<String> requestedProductIds = new java.util.ArrayList<>();

        @Override
        public ConfirmedPremiumResult confirm(ConfirmedPremiumRequest request) {
            requestedProductIds.add(request.productId());
            BigDecimal standardPremium = "RIDER_ID_001".equals(request.productId())
                    ? new BigDecimal("200.00") : new BigDecimal("1000.00");
            BigDecimal ratio = request.underwritingAdjustments().isEmpty()
                    ? BigDecimal.ZERO : request.underwritingAdjustments().get(0).value();
            BigDecimal totalPremium = standardPremium.multiply(BigDecimal.ONE.add(ratio));
            return new ConfirmedPremiumResult(
                    "CALC_" + request.productId(), "CONFIRMED", "ISSUANCE_CONFIRM", request.productId(),
                    request.productVersion(), request.currency(), standardPremium, totalPremium, "PP_V1",
                    "HASH_" + request.productId());
        }

        List<String> requestedProductIds() {
            return List.copyOf(requestedProductIds);
        }
    }

    /**
     * 产品服务端口桩：返回固定投保规则（等待期 30 天 / 犹豫期 15 天，对齐医疗险常见配置）。
     */
    private static class StubProductServicePort implements ProductServicePort {

        /** G14/g14-04a：管理费费率桩值。刻意取与历史硬编码兜底 0.015 不同的值——透传断言因此有区分力。 */
        private BigDecimal managementFeeRate = new BigDecimal("0.0234");

        /** 非 null 时 getManagementFeeRate 抛出，模拟远程依赖不可用。 */
        private RuntimeException managementFeeRateFailure;

        /** 记录最近一次被取费率的 productId。 */
        private String lastManagementFeeRateProductId;

        @Override
        public ProductBasicInfo getProductBasicInfo(String productId) {
            return new ProductBasicInfo(productId, productId, "测试产品", "V1.0", null, "EFFECTIVE");
        }

        @Override
        public com.titanium.metadata.enums.product.ProductEnum.IssuanceMode getIssuanceMode(String productId) {
            return com.titanium.metadata.enums.product.ProductEnum.IssuanceMode.TWO_STEP;
        }

        @Override
        public ProductIssueRules getIssueRules(String productId) {
            return new ProductIssueRules(0, 65, null, null, null, null, List.of(), List.of(), List.of(), 30, 15, null,
                    false, List.of(), List.of(), null, List.of(), null, null, false, false);
        }

        @Override
        public ProductSumInsuredTiers getCoverageSumInsuredConfigs(String productId) {
            // 本桩不模拟责任档次配置：空 coverages ⇒ 聚合按「产品未配置档次」放行（档次校验另有专项测试）
            return new ProductSumInsuredTiers(productId, List.of());
        }

        @Override
        public BigDecimal getManagementFeeRate(String productId) {
            this.lastManagementFeeRateProductId = productId;
            if (managementFeeRateFailure != null) {
                throw managementFeeRateFailure;
            }
            return managementFeeRate;
        }

        void setManagementFeeRate(BigDecimal rate) {
            this.managementFeeRate = rate;
        }

        void failManagementFeeRate(RuntimeException failure) {
            this.managementFeeRateFailure = failure;
        }

        @Override
        public List<ProductClauseRef> getClauseRefs(String productId, String tenantId) {
            return List.of();
        }
    }

    /**
     * 条款服务端口桩：无条款绑定（本测试聚焦 Saga 编排流转，责任快照装配另有专项测试）。
     */
    private static class StubClauseServicePort implements ClauseServicePort {
        @Override
        public com.titanium.policy.valueobject.policy.ClauseSnapshot fetchClauseSnapshot(String clauseId,
                                                                                         boolean isMainClause,
                                                                                         String tenantId) {
            return null;
        }

        @Override
        public List<com.titanium.policy.valueobject.policy.CoverageSnapshot> fetchCoverageSnapshots(String clauseId,
                                                                                                    String tenantId) {
            return List.of();
        }
    }

    /**
     * 计费服务端口桩：开单恒成功（出单主链路不因计费失败中断）。
     */
    private static class StubBillingServicePort implements BillingServicePort {
        private PremiumScheduleRequest lastSchedule;
        private RuntimeException scheduleFailure;

        @Override
        public BillingResult createPremiumBill(PremiumBillRequest request) {
            return new BillingResult(true, "BILL_001", "ACCOUNT_001");
        }

        @Override
        public void generatePremiumSchedule(PremiumScheduleRequest request) {
            this.lastSchedule = request;
            if (scheduleFailure != null) {
                throw scheduleFailure;
            }
        }

        @Override
        public List<UnpaidBill> getUnpaidBills(String policyId) {
            return List.of();
        }

        PremiumScheduleRequest lastSchedule() {
            return lastSchedule;
        }

        void failSchedule(RuntimeException failure) {
            this.scheduleFailure = failure;
        }
    }

    /**
     * 投资账户端口桩：非投连保单不触发，返回固定账户ID；记录开户入参供 G14/g14-04a 费率透传断言。
     */
    private static class StubInvestmentAccountPort implements InvestmentAccountPort {

        /** 记录最近一次 openAccount 的入参（未调用时为 null）。 */
        private String lastPolicyId;
        private PolicyForm lastForm;
        private BigDecimal lastManagementFeeRate;
        private boolean openAccountCalled;

        @Override
        public String openAccount(String policyId, PolicyForm form,
                com.titanium.metadata.valueobject.Money premium, BigDecimal managementFeeRate) {
            this.openAccountCalled = true;
            this.lastPolicyId = policyId;
            this.lastForm = form;
            this.lastManagementFeeRate = managementFeeRate;
            return "ACC_001";
        }

        @Override
        public com.titanium.metadata.valueobject.Money accountValue(String accountId) {
            return null;
        }
    }
}
