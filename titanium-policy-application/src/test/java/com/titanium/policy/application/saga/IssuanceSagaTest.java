package com.titanium.policy.application.saga;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.test.matchers.Matchers;
import org.axonframework.test.saga.SagaTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;
import com.titanium.policy.application.orchestration.issuance.assembler.InsuranceLineAssembler;
import com.titanium.policy.application.orchestration.issuance.assembler.PolicyProductAssembler;
import com.titanium.policy.command.CreatePolicyCommand;
import com.titanium.policy.command.ReceiveUnderwritingResultCommand;
import com.titanium.policy.command.TriggerIssuanceCommand;
import com.titanium.policy.event.insurance.InsuranceCreatedEvent;
import com.titanium.policy.event.insurance.InsuranceIssuedEvent;
import com.titanium.policy.event.insurance.InsuranceSubmittedForUnderwritingEvent;
import com.titanium.policy.event.insurance.UnderwritingResultReceivedEvent;
import com.titanium.policy.generator.PolicyNoGenerator;
import com.titanium.policy.port.BillingServicePort;
import com.titanium.policy.port.ClauseServicePort;
import com.titanium.policy.port.InvestmentAccountPort;
import com.titanium.policy.port.PremiumCalculationGateway;
import com.titanium.policy.port.PremiumCalculationGateway.StandardPremiumRequest;
import com.titanium.policy.port.PremiumCalculationGateway.StandardPremiumResult;
import com.titanium.policy.port.ProductServicePort;
import com.titanium.policy.port.UnderwritingDecisionGateway;
import com.titanium.policy.service.impl.PolicyIssuanceDomainServiceImpl;
import com.titanium.policy.valueobject.billing.BillingResult;
import com.titanium.policy.valueobject.billing.PremiumBillRequest;
import com.titanium.policy.valueobject.billing.PremiumScheduleRequest;
import com.titanium.policy.valueobject.insurance.UnderwritingDecisionRequest;
import com.titanium.policy.valueobject.insurance.UnderwritingResult;
import com.titanium.policy.valueobject.product.ProductBasicInfo;
import com.titanium.policy.valueobject.product.ProductClauseRef;
import com.titanium.policy.valueobject.product.ProductIssueRules;

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
    private static final String TENANT_ID    = "TENANT_001";

    private SagaTestFixture<IssuanceSaga> fixture;
    private StubUnderwritingDecisionGateway underwritingGateway;

    @BeforeEach
    public void setUp() {
        fixture = new SagaTestFixture<>(IssuanceSaga.class);
        underwritingGateway = new StubUnderwritingDecisionGateway();
        fixture.registerCommandGateway(CommandGateway.class);
        fixture.registerResource(underwritingGateway);
        fixture.registerResource(new PolicyNoGenerator());
        fixture.registerResource(new PolicyIssuanceDomainServiceImpl());
        // BILL-2：注册保费计算网关桩（返回固定保费，不依赖 billing 服务）
        fixture.registerResource(new StubPremiumCalculationGateway());
        // 险种段化后 Saga 新增依赖：产品配置（等待期/犹豫期）、条款责任装配、计费开单、投资开户
        StubProductServicePort productServicePort = new StubProductServicePort();
        fixture.registerResource(productServicePort);
        fixture.registerResource(new StubClauseServicePort());
        fixture.registerResource(new StubBillingServicePort());
        fixture.registerResource(new StubInvestmentAccountPort());
        fixture.registerResource(new PolicyProductAssembler(productServicePort, new StubClauseServicePort(),
                new InsuranceLineAssembler(productServicePort)));
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
        fixture.givenAggregate(INSURANCE_ID).published(insuranceCreatedEvent())
                .whenAggregate(INSURANCE_ID).publishes(insuranceIssuedEvent())
                .expectActiveSagas(0)
                .expectDispatchedCommandsMatching(Matchers.payloadsMatching(Matchers.exactSequenceOf(
                        Matchers.<CreatePolicyCommand>predicate(cmd ->
                                cmd instanceof CreatePolicyCommand c
                                        && INSURANCE_ID.equals(c.insuranceId())
                                        && TENANT_ID.equals(c.tenantId())
                                        && PolicyForm.INDIVIDUAL == c.policyForm()
                                        && c.policyId() != null
                                        && c.policyNo() != null),
                        Matchers.andNoMore())));
    }

    // ==================== 测试夹具数据 ====================

    private InsuranceCreatedEvent insuranceCreatedEvent() {
        return new InsuranceCreatedEvent(INSURANCE_ID, "INS_NO_001", null, PolicyForm.INDIVIDUAL, "HOLDER_001", 1,
                new BigDecimal("1000.00"), LocalDateTime.now(), LocalDateTime.now().plusYears(1),
                List.of(mainInsuranceLine()), 0, null, null, null, null, "BIZ_NO_001", null, LocalDateTime.now(),
                TENANT_ID, new BigDecimal("500000"), "ANNUAL", 20);
    }

    /** 主险段测试夹具（一单一险场景，段列表长度 1） */
    private com.titanium.policy.entity.insurance.InsuranceLine mainInsuranceLine() {
        return new com.titanium.policy.entity.insurance.InsuranceLine("LINE_001", 1,
                com.titanium.metadata.enums.product.ProductEnum.ProductCategory.MAIN, null, "PRODUCT_001",
                "PRODUCT_001", "测试产品", null,
                com.titanium.metadata.valueobject.Money.of(new BigDecimal("500000"), "CNY"),
                com.titanium.metadata.valueobject.Money.of(new BigDecimal("1000.00"), "CNY"), null, null, List.of(),
                null, null, com.titanium.metadata.enums.policy.PolicyLineStatus.UNDERWRITING);
    }

    private InsuranceSubmittedForUnderwritingEvent insuranceSubmittedEvent() {
        return new InsuranceSubmittedForUnderwritingEvent(INSURANCE_ID, "INS_NO_001", "HOLDER_001", 1,
                new BigDecimal("1000.00"), "CNY", LocalDateTime.now(), LocalDateTime.now().plusYears(1),
                List.of("PRODUCT_001"), 0, PolicyForm.INDIVIDUAL, TENANT_ID);
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

        void setResult(UnderwritingResult result) {
            this.result = result;
        }

        @Override
        public UnderwritingResult requestDecision(UnderwritingDecisionRequest request) {
            return result;
        }
    }

    /**
     * 保费计算网关桩：返回固定保费，保证测试确定性（不依赖 billing 域）
     */
    private static class StubPremiumCalculationGateway implements PremiumCalculationGateway {
        @Override
        public StandardPremiumResult calculatePremium(StandardPremiumRequest request) {
            return new StandardPremiumResult(new BigDecimal("1000.00"),
                    new BigDecimal("1000.00"), 1, "CNY");
        }
    }

    /**
     * 产品服务端口桩：返回固定投保规则（等待期 30 天 / 犹豫期 15 天，对齐医疗险常见配置）。
     */
    private static class StubProductServicePort implements ProductServicePort {
        @Override
        public ProductBasicInfo getProductBasicInfo(String productId, String tenantId) {
            return new ProductBasicInfo(productId, productId, "测试产品", "V1.0", null, "EFFECTIVE");
        }

        @Override
        public com.titanium.metadata.enums.product.ProductEnum.IssuanceMode getIssuanceMode(String productId,
                                                                                            String tenantId) {
            return com.titanium.metadata.enums.product.ProductEnum.IssuanceMode.TWO_STEP;
        }

        @Override
        public ProductIssueRules getIssueRules(String productId, String tenantId) {
            return new ProductIssueRules(0, 65, null, null, null, null, List.of(), List.of(), List.of(), 30, 15, null,
                    false, List.of(), List.of(), null, List.of(), null, null, false, false);
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
        @Override
        public BillingResult createPremiumBill(PremiumBillRequest request) {
            return new BillingResult(true, "BILL_001");
        }

        @Override
        public void generatePremiumSchedule(PremiumScheduleRequest request) {
            // 期缴计划生成为副作用，测试无需断言
        }
    }

    /**
     * 投资账户端口桩：非投连保单不触发，返回固定账户ID。
     */
    private static class StubInvestmentAccountPort implements InvestmentAccountPort {
        @Override
        public String openAccount(String policyId, PolicyForm form, com.titanium.metadata.valueobject.Money premium,
                                  String tenantId) {
            return "ACC_001";
        }

        @Override
        public com.titanium.metadata.valueobject.Money accountValue(String accountId, String tenantId) {
            return null;
        }
    }
}
