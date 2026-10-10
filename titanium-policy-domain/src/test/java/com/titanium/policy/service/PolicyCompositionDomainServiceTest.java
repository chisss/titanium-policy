package com.titanium.policy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyLineStatus;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.entity.policy.PolicyProduct;
import com.titanium.policy.service.impl.PolicyCompositionDomainServiceImpl;
import com.titanium.policy.valueobject.RuleDecision;

/**
 * 保单构成领域服务测试
 * <p>
 * 验证一单多险的四条聚合不变量。纯领域服务无 Port / 无基础设施依赖，直接 {@code new} 实例化，
 * 无需 Spring 容器。
 * </p>
 */
class PolicyCompositionDomainServiceTest {

    private final PolicyCompositionDomainService service = new PolicyCompositionDomainServiceImpl();

    /** 构造主险段 */
    private static PolicyProduct mainLine(String id, int lineNo, String premium) {
        return line(id, lineNo, ProductCategory.MAIN, null, premium, PolicyLineStatus.ACCEPTED);
    }

    /** 构造附加险段 */
    private static PolicyProduct riderLine(String id, int lineNo, String parentId, String premium) {
        return line(id, lineNo, ProductCategory.RIDER, parentId, premium, PolicyLineStatus.ACCEPTED);
    }

    /**
     * 构造指定产品ID的主险段。
     * <p>
     * 🔴 主险段唯一性判据是 <b>productId</b>（G11/g11-04 ②）——不能按险种（交强与商业同属 AUTO）、
     * 也不能按产品类别（车损与三者同为自愿主险），故用例须能独立指定 productId。
     * </p>
     */
    private static PolicyProduct mainLineOfProduct(String id, int lineNo, String productId, String premium) {
        return new PolicyProduct(id, lineNo, ProductCategory.MAIN, null, productId, "CODE-" + id, "产品" + id, "V1.0",
                InsuranceProductType.MEDICAL, Money.of(new java.math.BigDecimal("4000000"), "CNY"),
                premium != null ? Money.of(new java.math.BigDecimal(premium), "CNY") : null, null, null, null,
                PolicyLineStatus.ACCEPTED, List.of(), List.of(), List.of());
    }

    /** 构造指定状态的险种段 */
    private static PolicyProduct line(String id, int lineNo, ProductCategory category, String parentId, String premium,
                                      PolicyLineStatus status) {
        return new PolicyProduct(id, lineNo, category, parentId, "prod-" + id, "CODE-" + id, "产品" + id, "V1.0",
                InsuranceProductType.MEDICAL, Money.of(new java.math.BigDecimal("4000000"), "CNY"),
                premium != null ? Money.of(new java.math.BigDecimal(premium), "CNY") : null, null, null, null, status,
                List.of(), List.of(), List.of());
    }

    @Test
    @DisplayName("单主险保单构成合法（单险种即段列表长度为1，无需特例）")
    void shouldAcceptSingleMainLine() {
        RuleDecision decision = service.validate(List.of(mainLine("L1", 1, "5000")),
                Money.of(new java.math.BigDecimal("5000"), "CNY"));

        assertTrue(decision.passed());
        assertNull(decision.errorCode());
    }

    @Test
    @DisplayName("一主险两附加险构成合法，总保费为三段之和")
    void shouldAcceptMainWithTwoRiders() {
        List<PolicyProduct> lines = List.of(
                mainLine("L1", 1, "5000"),
                riderLine("L2", 2, "L1", "800"),
                riderLine("L3", 3, "L1", "200"));

        RuleDecision decision = service.validate(lines, Money.of(new java.math.BigDecimal("6000"), "CNY"));

        assertTrue(decision.passed());
        assertEquals(new java.math.BigDecimal("6000.00"), service.sumPremium(lines).value());
    }

    @Test
    @DisplayName("无主险段被拒绝")
    void shouldRejectWhenNoMainLine() {
        RuleDecision decision = service.validate(List.of(riderLine("L2", 2, "L1", "800")), null);

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.COMPOSITION_MAIN_LINE_REQUIRED, decision.errorCode());
    }

    @Test
    @DisplayName("同一产品的两个主险段被拒绝（主险段间 productId 唯一，G11 方案 B ②）")
    void shouldRejectWhenMultipleMainLinesShareProduct() {
        RuleDecision decision = service.validate(
                List.of(mainLineOfProduct("L1", 1, "PROD-SAME", "5000"),
                        mainLineOfProduct("L2", 2, "PROD-SAME", "3000")), null);

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.COMPOSITION_MULTIPLE_MAIN_LINES, decision.errorCode());
    }

    @Test
    @DisplayName("交强 + 商业同单：两个不同产品ID的主险段合法（G11 方案 B 正例，AC-05）")
    void shouldAcceptMainLinesAcrossDifferentProducts() {
        List<PolicyProduct> lines = List.of(
                mainLineOfProduct("L1", 1, "PROD-COMPULSORY", "950"),
                mainLineOfProduct("L2", 2, "PROD-COMMERCIAL", "3000"));

        RuleDecision decision = service.validate(lines, Money.of(new java.math.BigDecimal("3950"), "CNY"));

        assertTrue(decision.passed());
        assertEquals(new java.math.BigDecimal("3950.00"), service.sumPremium(lines).value());
    }

    @Test
    @DisplayName("附加险可依附任一主险段：父段指向第二主险段放行（G11 方案 B ③，AC-05 反-2）")
    void shouldAcceptRiderAttachedToSecondMainLine() {
        RuleDecision decision = service.validate(
                List.of(mainLineOfProduct("L1", 1, "PROD-COMPULSORY", "950"),
                        mainLineOfProduct("L2", 2, "PROD-COMMERCIAL", "3000"),
                        riderLine("L3", 3, "L2", "800")), null);

        assertTrue(decision.passed());
        assertNull(decision.errorCode());
    }

    @Test
    @DisplayName("附加险依附的主险段不存在时被拒绝")
    void shouldRejectWhenRiderParentNotFound() {
        RuleDecision decision = service.validate(
                List.of(mainLine("L1", 1, "5000"), riderLine("L2", 2, "NOT_EXIST", "800")), null);

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.COMPOSITION_RIDER_PARENT_INVALID, decision.errorCode());
    }

    @Test
    @DisplayName("附加险未指定依附主险段时被拒绝")
    void shouldRejectWhenRiderParentBlank() {
        RuleDecision decision = service.validate(
                List.of(mainLine("L1", 1, "5000"), riderLine("L2", 2, null, "800")), null);

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.COMPOSITION_RIDER_PARENT_REQUIRED, decision.errorCode());
    }

    @Test
    @DisplayName("总保费与段保费合计不一致时被拒绝（保费守恒）")
    void shouldRejectWhenPremiumNotConserved() {
        RuleDecision decision = service.validate(
                List.of(mainLine("L1", 1, "5000"), riderLine("L2", 2, "L1", "800")),
                Money.of(new java.math.BigDecimal("9999"), "CNY"));

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.COMPOSITION_PREMIUM_NOT_CONSERVED, decision.errorCode());
    }

    @Test
    @DisplayName("拒保段保费不计入总保费（保费守恒按有效段口径）")
    void shouldExcludeRejectedLineFromPremium() {
        List<PolicyProduct> lines = List.of(
                mainLine("L1", 1, "5000"),
                line("L2", 2, ProductCategory.RIDER, "L1", "800", PolicyLineStatus.REJECTED));

        // 总保费只含主险 5000，拒保的附加险 800 不计入
        RuleDecision decision = service.validate(lines, Money.of(new java.math.BigDecimal("5000"), "CNY"));

        assertTrue(decision.passed());
        assertEquals(new java.math.BigDecimal("5000.00"), service.sumPremium(lines).value());
    }

    @Test
    @DisplayName("段序号重复时被拒绝")
    void shouldRejectDuplicateLineNo() {
        RuleDecision decision = service.validate(
                List.of(mainLine("L1", 1, "5000"), riderLine("L2", 1, "L1", "800")), null);

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.COMPOSITION_LINE_ID_DUPLICATE, decision.errorCode());
    }

    @Test
    @DisplayName("段ID重复时被拒绝")
    void shouldRejectDuplicateLineId() {
        RuleDecision decision = service.validate(
                List.of(mainLine("L1", 1, "5000"), riderLine("L1", 2, "L1", "800")), null);

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.COMPOSITION_LINE_ID_DUPLICATE, decision.errorCode());
    }

    @Test
    @DisplayName("空险种段列表被拒绝")
    void shouldRejectEmptyLines() {
        RuleDecision decision = service.validate(List.of(), null);

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.COMPOSITION_LINES_EMPTY, decision.errorCode());
    }

    @Test
    @DisplayName("未声明总保费时跳过保费守恒校验（出单期由服务汇总产生）")
    void shouldSkipPremiumCheckWhenTotalNotDeclared() {
        RuleDecision decision = service.validate(
                List.of(mainLine("L1", 1, "5000"), riderLine("L2", 2, "L1", "800")), null);

        assertTrue(decision.passed());
    }
}
