package com.titanium.policy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.customer.CustomerEnum.CustomerGender;
import com.titanium.metadata.enums.insurance.SubjectType;
import com.titanium.metadata.enums.policy.BeneficiaryType;
import com.titanium.metadata.enums.product.ProductEnum.PaymentFrequency;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.service.impl.IssuanceEligibilityDomainServiceImpl;
import com.titanium.policy.valueobject.IssuancePlanLine;
import com.titanium.policy.valueobject.IssuanceRequest;
import com.titanium.policy.valueobject.RuleDecision;
import com.titanium.policy.valueobject.product.ProductIssueRules;

/**
 * 投保要素校验领域服务测试
 * <p>
 * 覆盖单据级规则（投保人/被保险人/受益份额）与段级规则（年龄/保额/职业/地域/人数/期间/缴费）。
 * 纯领域服务无 Port 依赖，直接 {@code new} 实例化。
 * </p>
 */
class IssuanceEligibilityDomainServiceTest {

    private static final String PRODUCT_ID = "PROD_MED_001";

    private final IssuanceEligibilityDomainService service = new IssuanceEligibilityDomainServiceImpl();

    /** 医疗险典型规则：0-65 岁、保额 100 万~600 万、禁高危职业、年缴 */
    private static ProductIssueRules medicalRules() {
        return new ProductIssueRules(0, 65, new BigDecimal("1000000"), new BigDecimal("6000000"), 5, null,
                List.of("HIGH_RISK_MINER", "HIGH_RISK_DIVER"), List.of(), List.of("XINJIANG"), 30, 15, null, false,
                List.of(PaymentFrequency.ANNUAL, PaymentFrequency.LUMP_SUM), List.of(1, 5, 10), null, List.of(1, 5, 10),
                null, new BigDecimal("5000000"), false, false);
    }

    /** 构造一个合格的医疗险出单请求（35 岁男，保额 400 万，年缴 1 年期） */
    private static IssuanceRequest validRequest() {
        return requestWith(insuredParties(35), planLine(Map.of()));
    }

    private static IssuanceRequest requestWith(InsuredPartyList partyList, IssuancePlanLine line) {
        return new IssuanceRequest("BIZ_001", "TENANT_001", null, null, null, "CUST_HOLDER", partyList, null, null,
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now().plusYears(1), null, null, null, null,
                List.of(line), Money.of(new BigDecimal("5000"), "CNY"), null);
    }

    private static IssuancePlanLine planLine(Map<String, Object> subjectAttributes) {
        return planLine(new BigDecimal("4000000"), PaymentFrequency.ANNUAL, 1, subjectAttributes);
    }

    private static IssuancePlanLine planLine(BigDecimal sumInsured, PaymentFrequency frequency, Integer years,
                                             Map<String, Object> subjectAttributes) {
        IssuancePlanLine.SubjectIntent subject = new IssuancePlanLine.SubjectIntent(SubjectType.PERSON, "CUST_INSURED",
                "张三", null, "SELF", subjectAttributes);
        return new IssuancePlanLine(1, PRODUCT_ID, ProductCategory.MAIN, null,
                sumInsured != null ? Money.of(sumInsured, "CNY") : null, 1, null, frequency, years, List.of(subject),
                null);
    }

    /** 构造含指定年龄被保险人的参与方清单（受益人份额 100%） */
    private static InsuredPartyList insuredParties(int age) {
        InsuredPartyList.HolderInfo holder = new InsuredPartyList.HolderInfo("CUST_HOLDER", "H1", "张三", null, null,
                null);
        InsuredPartyList.InsuredInfo insured = new InsuredPartyList.InsuredInfo("CUST_INSURED", "I1", "张三", null, null,
                age, CustomerGender.MALE, null);
        InsuredPartyList.BeneficiaryInfo beneficiary = new InsuredPartyList.BeneficiaryInfo("CUST_BENE", "B1", "李四",
                null, null, BeneficiaryType.DEATH, 1, 1.0d);
        return new InsuredPartyList("L1", holder, List.of(insured), List.of(beneficiary));
    }

    @Test
    @DisplayName("合格投保要素通过校验")
    void shouldAcceptValidRequest() {
        RuleDecision decision = service.validate(validRequest(), Map.of(PRODUCT_ID, medicalRules()));

        assertTrue(decision.passed(), "预期通过，实际拒绝: " + decision.defaultMessage());
    }

    @Test
    @DisplayName("投保人缺失被拒绝（单据级规则）")
    void shouldRejectWhenHolderMissing() {
        IssuanceRequest request = new IssuanceRequest("BIZ_001", "TENANT_001", null, null, null, null,
                insuredParties(35), null, null, null, null, null, null, null, null, List.of(planLine(Map.of())), null,
                null);

        RuleDecision decision = service.validate(request, Map.of(PRODUCT_ID, medicalRules()));

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.ELIGIBILITY_HOLDER_REQUIRED, decision.errorCode());
    }

    @Test
    @DisplayName("无被保险人被拒绝（单据级规则）")
    void shouldRejectWhenNoInsured() {
        InsuredPartyList emptyInsured = new InsuredPartyList("L1",
                new InsuredPartyList.HolderInfo("CUST_HOLDER", "H1", "张三", null, null, null), List.of(), List.of());

        RuleDecision decision = service.validate(requestWith(emptyInsured, planLine(Map.of())),
                Map.of(PRODUCT_ID, medicalRules()));

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.ELIGIBILITY_INSURED_REQUIRED, decision.errorCode());
    }

    @Test
    @DisplayName("受益份额合计不足 100% 被拒绝（单据级规则）")
    void shouldRejectWhenBeneficiaryRatioInvalid() {
        InsuredPartyList.HolderInfo holder = new InsuredPartyList.HolderInfo("CUST_HOLDER", "H1", "张三", null, null,
                null);
        InsuredPartyList.InsuredInfo insured = new InsuredPartyList.InsuredInfo("CUST_INSURED", "I1", "张三", null, null,
                35, CustomerGender.MALE, null);
        // 同一顺位仅 60%，不满 100%
        InsuredPartyList.BeneficiaryInfo partial = new InsuredPartyList.BeneficiaryInfo("CUST_BENE", "B1", "李四", null,
                null, BeneficiaryType.DEATH, 1, 0.6d);
        InsuredPartyList partyList = new InsuredPartyList("L1", holder, List.of(insured), List.of(partial));

        RuleDecision decision = service.validate(requestWith(partyList, planLine(Map.of())),
                Map.of(PRODUCT_ID, medicalRules()));

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.ELIGIBILITY_BENEFICIARY_RATIO_INVALID, decision.errorCode());
    }

    @Test
    @DisplayName("超过最大投保年龄被拒绝，并携带违反段序号")
    void shouldRejectWhenAgeExceedsMax() {
        RuleDecision decision = service.validate(requestWith(insuredParties(70), planLine(Map.of())),
                Map.of(PRODUCT_ID, medicalRules()));

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.ELIGIBILITY_AGE_EXCEEDS_MAX, decision.errorCode());
        assertEquals(1, decision.lineNo(), "段级违反须携带段序号，便于一单多险定位");
    }

    @Test
    @DisplayName("保额低于产品最小保额被拒绝")
    void shouldRejectWhenSumInsuredBelowMin() {
        IssuancePlanLine line = planLine(new BigDecimal("500000"), PaymentFrequency.ANNUAL, 1, Map.of());

        RuleDecision decision = service.validate(requestWith(insuredParties(35), line),
                Map.of(PRODUCT_ID, medicalRules()));

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.ELIGIBILITY_SUM_INSURED_BELOW_MIN, decision.errorCode());
    }

    @Test
    @DisplayName("保额超过产品最大保额被拒绝")
    void shouldRejectWhenSumInsuredExceedsMax() {
        IssuancePlanLine line = planLine(new BigDecimal("8000000"), PaymentFrequency.ANNUAL, 1, Map.of());

        RuleDecision decision = service.validate(requestWith(insuredParties(35), line),
                Map.of(PRODUCT_ID, medicalRules()));

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.ELIGIBILITY_SUM_INSURED_EXCEEDS_MAX, decision.errorCode());
    }

    @Test
    @DisplayName("职业在禁保清单内被拒绝")
    void shouldRejectForbiddenOccupation() {
        RuleDecision decision = service.validate(
                requestWith(insuredParties(35), planLine(Map.of("occupation", "HIGH_RISK_MINER"))),
                Map.of(PRODUCT_ID, medicalRules()));

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.ELIGIBILITY_OCCUPATION_FORBIDDEN, decision.errorCode());
    }

    @Test
    @DisplayName("地域在禁保清单内被拒绝")
    void shouldRejectForbiddenRegion() {
        RuleDecision decision = service.validate(
                requestWith(insuredParties(35), planLine(Map.of("region", "XINJIANG"))),
                Map.of(PRODUCT_ID, medicalRules()));

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.ELIGIBILITY_REGION_FORBIDDEN, decision.errorCode());
    }

    @Test
    @DisplayName("缴费频率不在产品允许集内被拒绝")
    void shouldRejectDisallowedPaymentFrequency() {
        IssuancePlanLine line = planLine(new BigDecimal("4000000"), PaymentFrequency.MONTHLY, 1, Map.of());

        RuleDecision decision = service.validate(requestWith(insuredParties(35), line),
                Map.of(PRODUCT_ID, medicalRules()));

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.ELIGIBILITY_PAYMENT_FREQUENCY_NOT_ALLOWED, decision.errorCode());
    }

    @Test
    @DisplayName("缴费年数不在产品允许集内被拒绝")
    void shouldRejectDisallowedPaymentTerm() {
        IssuancePlanLine line = planLine(new BigDecimal("4000000"), PaymentFrequency.ANNUAL, 7, Map.of());

        RuleDecision decision = service.validate(requestWith(insuredParties(35), line),
                Map.of(PRODUCT_ID, medicalRules()));

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.ELIGIBILITY_PAYMENT_TERM_NOT_ALLOWED, decision.errorCode());
    }

    @Test
    @DisplayName("被保险人数超过产品上限被拒绝")
    void shouldRejectWhenInsuredCountExceedsMax() {
        InsuredPartyList.HolderInfo holder = new InsuredPartyList.HolderInfo("CUST_HOLDER", "H1", "张三", null, null,
                null);
        java.util.List<InsuredPartyList.InsuredInfo> many = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            many.add(new InsuredPartyList.InsuredInfo("CUST_" + i, "I" + i, "被保人" + i, null, null, 35,
                    CustomerGender.MALE, null));
        }
        InsuredPartyList partyList = new InsuredPartyList("L1", holder, many, List.of());

        RuleDecision decision = service.validate(requestWith(partyList, planLine(Map.of())),
                Map.of(PRODUCT_ID, medicalRules()));

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.ELIGIBILITY_INSURED_COUNT_EXCEEDS_MAX, decision.errorCode());
    }

    @Test
    @DisplayName("产品规则缺失时跳过段级校验（不阻断出单）")
    void shouldSkipLineRulesWhenProductRulesMissing() {
        RuleDecision decision = service.validate(requestWith(insuredParties(70), planLine(Map.of())), Map.of());

        assertTrue(decision.passed(), "产品规则未取到时不应因段级规则拒绝");
    }
}
