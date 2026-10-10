package com.titanium.policy.aggregate;

import static org.axonframework.test.matchers.Matchers.exactSequenceOf;
import static org.axonframework.test.matchers.Matchers.payloadsMatching;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.policy.PolicyLineStatus;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.command.ConvertProposalToInsuranceCommand;
import com.titanium.policy.entity.insurance.InsuranceLine;
import com.titanium.policy.event.insurance.InsuranceCreatedEvent;
import com.titanium.policy.exception.PolicyBusinessRuleException;
import com.titanium.policy.valueobject.product.CoverageSumInsuredConfig;
import com.titanium.policy.valueobject.product.CoverageSumInsuredConfig.SumInsuredTier;
import com.titanium.policy.valueobject.product.ProductSumInsuredTiers;

/**
 * 投保单段级保额档次校验测试（G02/AC-09）
 * <p>
 * 产品域为每条责任配置「档位清单 + 可填区间 + 是否开放自由填」，段级录入的保额须被至少一条责任
 * 接纳。本测试锁定 AC-09 的正例与<b>两条成因不同的反例</b>（超区间 / 未命中档位），以及三态语义
 * （未配置放行 / 未接线失败关闭 / 段上无保额不适用）。
 * </p>
 */
@DisplayName("投保单段级保额档次校验（G02/AC-09）")
class InsuranceSumInsuredTierTest {

    private static final String  INSURANCE_ID = "INSURANCE_001";
    private static final String  PRODUCT_ID   = "PROD_001";
    private static final String  TENANT_ID    = "TENANT_001";

    private FixtureConfiguration<Insurance> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Insurance.class);
    }

    // ==================== AC-09 正例 ====================

    @Test
    @DisplayName("正例：3 档 + 区间[10万,100万] + 开放自由填，录入 60 万创建成功")
    void shouldAcceptCustomAmountWithinRange() {
        fixture.givenNoPriorActivity()
                .when(command(line(new BigDecimal("600000")), tiers(config(true))))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(payloadsMatching(exactSequenceOf(createdEventWithOneLine())));
    }

    @Test
    @DisplayName("未开放自由填时，精确命中档位的保额仍被接纳（拒绝不是「一律拒绝」）")
    void shouldAcceptExactTierAmountWhenCustomAmountDisallowed() {
        fixture.givenNoPriorActivity()
                .when(command(line(new BigDecimal("500000")), tiers(config(false))))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(payloadsMatching(exactSequenceOf(createdEventWithOneLine())));
    }

    // ==================== AC-09 反例（两条成因不同，不得合并） ====================

    @Test
    @DisplayName("反例一：录入 200 万超出区间 [10万,100万] 且未命中任何档位，被拒绝")
    void shouldRejectAmountExceedingRangeAndOffAllTiers() {
        // 与反例二同走「未被任何责任档次接纳」出口，差异在输入（超区间 vs 区间内未命中档位）
        fixture.givenNoPriorActivity()
                .when(command(line(new BigDecimal("2000000")), tiers(config(true))))
                .expectException(PolicyBusinessRuleException.class)
                .expectExceptionMessage(CoreMatchers.allOf(
                        CoreMatchers.containsString("未被产品 " + PRODUCT_ID + " 的任何责任档次接纳"),
                        // 拒绝报文须携带产品档位全貌，供人工归因（G02 可归因要求）
                        CoreMatchers.containsString("自由填[是]")))
                .expectNoEvents();
    }

    @Test
    @DisplayName("反例二：未开放自由填时，录入 45 万虽在区间内但未命中档位，被拒绝")
    void shouldRejectAmountWithinRangeButOffAllTiersWhenCustomAmountDisallowed() {
        fixture.givenNoPriorActivity()
                .when(command(line(new BigDecimal("450000")), tiers(config(false))))
                .expectException(PolicyBusinessRuleException.class)
                .expectExceptionMessage(CoreMatchers.allOf(
                        CoreMatchers.containsString("未被产品 " + PRODUCT_ID + " 的任何责任档次接纳"),
                        CoreMatchers.containsString("自由填[否]")))
                .expectNoEvents();
    }

    // ==================== 三态语义 ====================

    @Test
    @DisplayName("产品未配置任何责任档次：放行（不配置是合法中间状态，存量产品不受阻）")
    void shouldPassWhenProductHasNoTierConfig() {
        fixture.givenNoPriorActivity()
                .when(command(line(new BigDecimal("600000")), tiersOf(List.of())))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(payloadsMatching(exactSequenceOf(createdEventWithOneLine())));
    }

    @Test
    @DisplayName("调用方未提供档次配置（未接线）且段上有保额：失败关闭，拒绝")
    void shouldRejectWhenTierConfigNotProvided() {
        fixture.givenNoPriorActivity()
                .when(command(line(new BigDecimal("600000")), null))
                .expectException(PolicyBusinessRuleException.class)
                .expectExceptionMessage(CoreMatchers.containsString("未提供产品保额档次配置"))
                .expectNoEvents();
    }

    @Test
    @DisplayName("段上保额为空：本规则不适用，即便未接线也放行（保额必填由受理阶段守护）")
    void shouldSkipCheckWhenLineCarriesNoSumInsured() {
        fixture.givenNoPriorActivity()
                .when(command(line(null), null))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(payloadsMatching(exactSequenceOf(createdEventWithOneLine())));
    }

    // ==================== 存在性量化（∃） ====================

    @Test
    @DisplayName("多责任存在量化：任一责任接纳即放行（不要求逐责任匹配）")
    void shouldPassWhenAnyCoverageAcceptsTheAmount() {
        // 责任 A 只接纳 [10万,20万] 且不开放自由填；责任 B 档位含 30 万 ⇒ 录入 30 万被 B 接纳
        CoverageSumInsuredConfig narrowCoverage = new CoverageSumInsuredConfig("COV_A",
                List.of(new SumInsuredTier("T1", new BigDecimal("100000"), List.of(), 1)),
                new BigDecimal("100000"), new BigDecimal("200000"), false, "T1");
        CoverageSumInsuredConfig wideCoverage = new CoverageSumInsuredConfig("COV_B",
                List.of(new SumInsuredTier("T1", new BigDecimal("300000"), List.of(), 1)),
                new BigDecimal("300000"), new BigDecimal("300000"), false, "T1");

        fixture.givenNoPriorActivity()
                .when(command(line(new BigDecimal("300000")), tiersOf(List.of(narrowCoverage, wideCoverage))))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(payloadsMatching(exactSequenceOf(createdEventWithOneLine())));
    }

    // ==================== 夹具 ====================

    /**
     * 匹配本次创建的 {@link InsuranceCreatedEvent}（忽略 {@code createTime} 等非确定性字段）。
     */
    private TypeSafeMatcher<InsuranceCreatedEvent> createdEventWithOneLine() {
        return new TypeSafeMatcher<>() {
            @Override
            protected boolean matchesSafely(InsuranceCreatedEvent event) {
                return INSURANCE_ID.equals(event.insuranceId()) && event.insuranceLines() != null
                        && event.insuranceLines().size() == 1;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText(
                        "InsuranceCreatedEvent[insuranceId=" + INSURANCE_ID + ", 险种段数=1]");
            }
        };
    }

    /**
     * 主险段（段序号 1，产品 {@value #PRODUCT_ID}）。
     *
     * @param sumInsured 段级保额；{@code null} 表示未录入
     */
    private InsuranceLine line(BigDecimal sumInsured) {
        return new InsuranceLine("LINE_001", 1, ProductCategory.MAIN, null, PRODUCT_ID, "PROD_CODE_001", "测试产品",
                null, sumInsured != null ? Money.of(sumInsured, "CNY") : null, Money.of(new BigDecimal("1000"), "CNY"),
                null, null, List.of(), null, null, PolicyLineStatus.UNDERWRITING);
    }

    /** AC-09 的三档配置：档位 10 万/50 万/100 万，区间 [10万,100万]。 */
    private CoverageSumInsuredConfig config(boolean allowCustomAmount) {
        return new CoverageSumInsuredConfig("COV_MAIN",
                List.of(new SumInsuredTier("T1", new BigDecimal("100000"), List.of(), 1),
                        new SumInsuredTier("T2", new BigDecimal("500000"), List.of(), 2),
                        new SumInsuredTier("T3", new BigDecimal("1000000"), List.of(), 3)),
                new BigDecimal("100000"), new BigDecimal("1000000"), allowCustomAmount, "T2");
    }

    /** 单责任档次载体。 */
    private List<ProductSumInsuredTiers> tiers(CoverageSumInsuredConfig coverage) {
        return tiersOf(List.of(coverage));
    }

    /** 指定责任清单的档次载体。 */
    private List<ProductSumInsuredTiers> tiersOf(List<CoverageSumInsuredConfig> coverages) {
        return List.of(new ProductSumInsuredTiers(PRODUCT_ID, coverages));
    }

    private ConvertProposalToInsuranceCommand command(InsuranceLine line, List<ProductSumInsuredTiers> sumInsuredTiers) {
        LocalDateTime periodStart = LocalDateTime.now();
        return ConvertProposalToInsuranceCommand.builder()
                .insuranceId(INSURANCE_ID)
                .insuranceNo("INS_001")
                .proposalId("PROPOSAL_001")
                .policyForm(PolicyForm.INDIVIDUAL)
                .applicantId("CUSTOMER_001")
                .insuredCount(1)
                .exactPremium(Money.of(new BigDecimal("1000"), "CNY"))
                .insurancePeriodStart(periodStart)
                .insurancePeriodEnd(periodStart.plusYears(1))
                .insuranceLines(List.of(line))
                .tenantId(TENANT_ID)
                .sumInsuredTiers(sumInsuredTiers)
                .build();
    }
}
