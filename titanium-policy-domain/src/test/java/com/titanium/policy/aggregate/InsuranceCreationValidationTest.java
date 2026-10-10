package com.titanium.policy.aggregate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.matchers.Matchers;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.policy.PolicyLineStatus;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.command.ConvertProposalToInsuranceCommand;
import com.titanium.policy.entity.insurance.InsuranceLine;
import com.titanium.policy.event.insurance.InsuranceCreatedEvent;
import com.titanium.policy.exception.PolicyBusinessRuleException;
import com.titanium.policy.valueobject.product.ProductSumInsuredTiers;

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

    /**
     * 🔴 G11/g11-04 ⑨ / AC-05 正例：交强 + 商业同单——两个不同产品ID的主险段在**投保单创建**
     * 即放行（第二道防线）。旧「有且仅有一个主险」判据会在此处拒绝，且此关卡比保单段构成更早。
     */
    @Test
    void acceptsTwoMainLinesOfDifferentProducts() {
        new AggregateTestFixture<>(Insurance.class)
                .givenNoPriorActivity()
                .when(command(List.of(mainLine("LINE_001", 1, "PRODUCT_COMPULSORY", "P_CMP"),
                        mainLine("LINE_002", 2, "PRODUCT_COMMERCIAL", "P_CMR"))))
                .expectEventsMatching(Matchers.payloadsMatching(Matchers.exactSequenceOf(
                        Matchers.<Object>predicate(event -> event instanceof InsuranceCreatedEvent created
                                && created.insuranceLines().size() == 2),
                        Matchers.andNoMore())));
    }

    /**
     * 🔴 G11/g11-04 ⑨ / AC-05 反-1（投保单侧）：主险段间 productId 唯一——同一产品出现两个主险段即拒，
     * 且不得半提交任何事件。
     */
    @Test
    void rejectsTwoMainLinesSharingSameProduct() {
        new AggregateTestFixture<>(Insurance.class)
                .givenNoPriorActivity()
                .when(command(List.of(mainLine("LINE_001", 1, "PRODUCT_SAME", "P_1"),
                        mainLine("LINE_002", 2, "PRODUCT_SAME", "P_2"))))
                .expectException(PolicyBusinessRuleException.class)
                .expectNoEvents();
    }

    /**
     * 🔴 G11/g11-04 ⑨ / AC-05 反-2（投保单侧）：附加险父段解析须认「主险段 id 集合」——
     * 依附**第二个**主险段的附加险不得被误拒（旧 {@code findFirst()} 只认首个主险的完整复现点）。
     */
    @Test
    void acceptsRiderAttachedToSecondMainLine() {
        new AggregateTestFixture<>(Insurance.class)
                .givenNoPriorActivity()
                .when(command(List.of(mainLine("LINE_001", 1, "PRODUCT_COMPULSORY", "P_CMP"),
                        mainLine("LINE_002", 2, "PRODUCT_COMMERCIAL", "P_CMR"),
                        riderLine("LINE_003", 3, "LINE_002", "PRODUCT_RIDER", "P_RDR"))))
                .expectSuccessfulHandlerExecution();
    }

    /** 构造主险段（产品ID/编码非空，满足段级前置校验）。 */
    private static InsuranceLine mainLine(String lineId, int lineNo, String productId, String productCode) {
        return assertableLine(lineId, lineNo, ProductCategory.MAIN, null, productId, productCode);
    }

    /** 构造附加险段（父段以段ID引用）。 */
    private static InsuranceLine riderLine(String lineId, int lineNo, String parentLineId, String productId,
                                           String productCode) {
        return assertableLine(lineId, lineNo, ProductCategory.RIDER, parentLineId, productId, productCode);
    }

    private static InsuranceLine assertableLine(String lineId, int lineNo, ProductCategory category,
                                                String parentLineId, String productId, String productCode) {
        return new InsuranceLine(lineId, lineNo, category, parentLineId, productId, productCode, "产品" + lineId,
                null, null, Money.of(new BigDecimal("500000"), "CNY"), Money.of(new BigDecimal("1000"), "CNY"),
                null, null, List.of(), null, null, PolicyLineStatus.UNDERWRITING);
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
                // G02/AC-09：段上有保额时聚合要求携带档次配置分量（null 即失败关闭）；
                // 显式空清单 = 「已知该产品未配置责任档次」⇒ 放行留痕，与本组用例的校验焦点无关
                .sumInsuredTiers(lines.stream()
                        .filter(line -> line.sumInsured() != null && line.productId() != null)
                        .map(InsuranceLine::productId)
                        .distinct()
                        .map(productId -> new ProductSumInsuredTiers(productId, List.of()))
                        .toList())
                .tenantId("TENANT_001")
                .build();
    }
}
