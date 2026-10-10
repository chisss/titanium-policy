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
import com.titanium.metadata.enums.underwriting.HealthDeclarationHandling;
import com.titanium.metadata.enums.underwriting.HealthDeclarationQuestion;
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.common.constant.PolicyConstants.LineExtendDataKey;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.service.impl.IssuanceEligibilityDomainServiceImpl;
import com.titanium.policy.service.impl.VinValidatorImpl;
import com.titanium.policy.valueobject.RuleDecision;
import com.titanium.policy.valueobject.issuance.IssuancePlanLine;
import com.titanium.policy.valueobject.issuance.IssuanceRequest;
import com.titanium.policy.valueobject.product.HealthDeclarationItem;
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

    private final IssuanceEligibilityDomainService service =
            new IssuanceEligibilityDomainServiceImpl(new VinValidatorImpl());

    /** 医疗险典型规则：0-65 岁、保额 100 万~600 万、禁高危职业、年缴 */
    private static ProductIssueRules medicalRules() {
        return new ProductIssueRules(0, 65, new BigDecimal("1000000"), new BigDecimal("6000000"), 5, null,
                List.of("HIGH_RISK_MINER", "HIGH_RISK_DIVER"), List.of(), List.of("XINJIANG"), 30, 15, null, false,
                List.of(PaymentFrequency.ANNUAL, PaymentFrequency.LUMP_SUM), List.of(1, 5, 10), null, List.of(1, 5, 10),
                null, new BigDecimal("5000000"), false, false);
    }

    /** 车险产品规则：车辆标的必须提供车牌、VIN 与初登日期。 */
    private static ProductIssueRules vehicleRules() {
        return new ProductIssueRules(null, null, new BigDecimal("10000"), new BigDecimal("1000000"), 5, null,
                List.of(), List.of(), List.of(), 0, 0, null, false,
                List.of(PaymentFrequency.ANNUAL, PaymentFrequency.LUMP_SUM), List.of(1), null, List.of(1),
                null, null, false, false, SubjectType.VEHICLE,
                List.of("licensePlate", "vin", "firstRegistrationDate"));
    }

    private static ProductIssueRules propertyRules() {
        return new ProductIssueRules(null, null, new BigDecimal("100000"), new BigDecimal("100000000"), 100, null,
                List.of(), List.of(), List.of(), 0, 0, null, false,
                List.of(PaymentFrequency.ANNUAL, PaymentFrequency.LUMP_SUM), List.of(1), null, List.of(1),
                null, new BigDecimal("5000000"), false, false, SubjectType.PROPERTY,
                List.of("propertyAddress", "propertyUsage", "fireProtectionLevel", "propertyValue"));
    }

    /** 财产险规则 + 显式「不允许多标的」声明（g11-06，24 参主构造器末位 Boolean.FALSE） */
    private static ProductIssueRules propertyRulesDisallowMultiple() {
        return new ProductIssueRules(null, null, new BigDecimal("100000"), new BigDecimal("100000000"), 100, null,
                List.of(), List.of(), List.of(), 0, 0, null, false,
                List.of(PaymentFrequency.ANNUAL, PaymentFrequency.LUMP_SUM), List.of(1), null, List.of(1),
                null, new BigDecimal("5000000"), false, false, SubjectType.PROPERTY,
                List.of("propertyAddress", "propertyUsage", "fireProtectionLevel", "propertyValue"), Boolean.FALSE);
    }

    /** 财产险规则 + 显式「允许多标的」声明（g11-06，24 参主构造器末位 Boolean.TRUE） */
    private static ProductIssueRules propertyRulesAllowMultiple() {
        return new ProductIssueRules(null, null, new BigDecimal("100000"), new BigDecimal("100000000"), 100, null,
                List.of(), List.of(), List.of(), 0, 0, null, false,
                List.of(PaymentFrequency.ANNUAL, PaymentFrequency.LUMP_SUM), List.of(1), null, List.of(1),
                null, new BigDecimal("5000000"), false, false, SubjectType.PROPERTY,
                List.of("propertyAddress", "propertyUsage", "fireProtectionLevel", "propertyValue"), Boolean.TRUE);
    }

    /**
     * 医疗险规则 + 自定义告知项清单（g12-02 AC-02，25 参主构造器）：住院史（必答+拒保）/
     * 手术史（必答+仅记录）/慢病（选答+拒保），覆盖「必答校验」「拒保拦截」「仅记录不拦截」三种形态。
     */
    private static ProductIssueRules medicalRulesWithHealthDeclaration() {
        return new ProductIssueRules(0, 65, new BigDecimal("1000000"), new BigDecimal("6000000"), 5, null,
                List.of("HIGH_RISK_MINER", "HIGH_RISK_DIVER"), List.of(), List.of("XINJIANG"), 30, 15, null, false,
                List.of(PaymentFrequency.ANNUAL, PaymentFrequency.LUMP_SUM), List.of(1, 5, 10), null, List.of(1, 5, 10),
                null, new BigDecimal("5000000"), false, false, null, List.of(), null,
                List.of(new HealthDeclarationItem(HealthDeclarationQuestion.HOSPITALIZATION_TWO_YEARS, true,
                                HealthDeclarationHandling.REJECT),
                        new HealthDeclarationItem(HealthDeclarationQuestion.SURGERY_HISTORY, true,
                                HealthDeclarationHandling.RECORD_ONLY),
                        new HealthDeclarationItem(HealthDeclarationQuestion.CHRONIC_DISEASE, false,
                                HealthDeclarationHandling.REJECT)));
    }

    /**
     * 医疗险方案行 + 段级自定义告知项答案（g12-02；结构：{@code extendData.healthDeclaration.answers}）。
     * <p>
     * 🔴 用例中的答案键一律用<b>字面量常量名</b>（如 {@code "HOSPITALIZATION_TWO_YEARS"}）——
     * 它即线上报文的键形态（m8-1103 判据：枚举常量名即跨域契约），锁死契约而非枚举内部实现。
     * </p>
     */
    private static IssuancePlanLine planLineWithHealthAnswers(Map<String, Object> answers) {
        Map<String, Object> extendData = Map.of(LineExtendDataKey.HEALTH_DECLARATION,
                Map.of(LineExtendDataKey.HEALTH_DECLARATION_ANSWERS, answers));
        IssuancePlanLine.SubjectIntent subject = new IssuancePlanLine.SubjectIntent(SubjectType.PERSON, "CUST_INSURED",
                "张三", null, "SELF", Map.of());
        return new IssuancePlanLine(1, PRODUCT_ID, ProductCategory.MAIN, null,
                Money.of(new BigDecimal("4000000"), "CNY"), 1, null, PaymentFrequency.ANNUAL, 1, List.of(subject),
                extendData);
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

    private static IssuancePlanLine vehicleLine(Map<String, Object> attributes) {
        IssuancePlanLine.SubjectIntent subject = new IssuancePlanLine.SubjectIntent(SubjectType.VEHICLE, null,
                "沪A12345", null, null, attributes);
        return new IssuancePlanLine(1, "PROD_AUTO_001", ProductCategory.MAIN, null,
                Money.of(new BigDecimal("200000"), "CNY"), 1, null, PaymentFrequency.ANNUAL, 1,
                List.of(subject), null);
    }

    private static IssuancePlanLine propertyLine(Map<String, Object> attributes) {
        IssuancePlanLine.SubjectIntent subject = new IssuancePlanLine.SubjectIntent(SubjectType.PROPERTY, null,
                "测试厂房", null, null, attributes);
        return new IssuancePlanLine(1, "PROD_PROPERTY_001", ProductCategory.MAIN, null,
                Money.of(new BigDecimal("5000000"), "CNY"), 1, null, PaymentFrequency.ANNUAL, 1,
                List.of(subject), null);
    }

    /** 财产险标的完整必填属性（对应 propertyRules 的 required 清单，可过属性校验以测后续规则） */
    private static Map<String, Object> completePropertyAttributes() {
        return Map.of("propertyAddress", "上海市某路1号", "propertyUsage", "FACTORY",
                "fireProtectionLevel", "A", "propertyValue", 5000000);
    }

    /** 构造含两个标的的财产险段（多标的形态，g11-06 校验用；两标的属性均完整） */
    private static IssuancePlanLine twoSubjectPropertyLine() {
        IssuancePlanLine.SubjectIntent first = new IssuancePlanLine.SubjectIntent(SubjectType.PROPERTY, null,
                "测试厂房A", null, null, completePropertyAttributes());
        IssuancePlanLine.SubjectIntent second = new IssuancePlanLine.SubjectIntent(SubjectType.PROPERTY, null,
                "测试厂房B", null, null, completePropertyAttributes());
        return new IssuancePlanLine(1, "PROD_PROPERTY_001", ProductCategory.MAIN, null,
                Money.of(new BigDecimal("5000000"), "CNY"), 1, null, PaymentFrequency.ANNUAL, 1,
                List.of(first, second), null);
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

    @Test
    @DisplayName("车险车辆标的缺少 VIN 被拒绝")
    void shouldRejectVehicleWhenRequiredAttributeMissing() {
        IssuancePlanLine line = vehicleLine(Map.of("licensePlate", "沪A12345", "firstRegistrationDate", "2024-01-01"));

        RuleDecision decision = service.validate(requestWith(insuredParties(35), line),
                Map.of("PROD_AUTO_001", vehicleRules()));

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.ELIGIBILITY_SUBJECT_ATTRIBUTE_MISSING, decision.errorCode());
        assertEquals(1, decision.lineNo());
        assertEquals("vin", decision.args().get(0));
    }

    @Test
    @DisplayName("财产/车辆标的属性齐全时通过校验")
    void shouldAcceptObjectSubjectWhenSchemaAttributesComplete() {
        // 第 9 位 2 即校验位（加权和 189 mod 11 = 2）；g11-02 起 VIN 须过 ISO 3779 校对，原样例第 9 位 0 为非法
        IssuancePlanLine line = vehicleLine(Map.of("licensePlate", "沪A12345", "vin", "LSVAA000201234567",
                "firstRegistrationDate", "2024-01-01"));

        RuleDecision decision = service.validate(requestWith(insuredParties(35), line),
                Map.of("PROD_AUTO_001", vehicleRules()));

        assertTrue(decision.passed(), "车辆必填属性齐全时应通过: " + decision.defaultMessage());
    }

    @Test
    @DisplayName("企业财产标的缺少地址被拒绝")
    void shouldRejectPropertyWhenRequiredAttributeMissing() {
        IssuancePlanLine line = propertyLine(Map.of("propertyUsage", "FACTORY", "fireProtectionLevel", "A",
                "propertyValue", 5000000));

        RuleDecision decision = service.validate(requestWith(insuredParties(35), line),
                Map.of("PROD_PROPERTY_001", propertyRules()));

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.ELIGIBILITY_SUBJECT_ATTRIBUTE_MISSING, decision.errorCode());
        assertEquals("propertyAddress", decision.args().get(0));
    }

    @Test
    @DisplayName("车险非法 VIN（校验位不符）被拒绝（g11-02）")
    void shouldRejectVehicleWhenVinCheckDigitInvalid() {
        // 仅第 9 位由 X 改为 0（该位权重 0，加权和不变，期望校验位仍为 X）——最小定因样本
        IssuancePlanLine line = vehicleLine(Map.of("licensePlate", "沪A12345", "vin", "1M8GDM9A0KP042788",
                "firstRegistrationDate", "2024-01-01"));

        RuleDecision decision = service.validate(requestWith(insuredParties(35), line),
                Map.of("PROD_AUTO_001", vehicleRules()));

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.ELIGIBILITY_VIN_INVALID, decision.errorCode());
        assertEquals(1, decision.lineNo());
        assertEquals("1M8GDM9A0KP042788", decision.args().get(0), "应报出非法 VIN 原文");
    }

    @Test
    @DisplayName("车险非法 VIN（含 I/O/Q）被拒绝（g11-02）")
    void shouldRejectVehicleWhenVinContainsIllegalLetter() {
        IssuancePlanLine line = vehicleLine(Map.of("licensePlate", "沪A12345", "vin", "1M8GDM9AXKP04278O",
                "firstRegistrationDate", "2024-01-01"));

        RuleDecision decision = service.validate(requestWith(insuredParties(35), line),
                Map.of("PROD_AUTO_001", vehicleRules()));

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.ELIGIBILITY_VIN_INVALID, decision.errorCode());
    }

    @Test
    @DisplayName("多车标的逐一校验：任一 VIN 非法即拒（g11-02）")
    void shouldRejectWhenAnyVehicleVinInvalid() {
        IssuancePlanLine.SubjectIntent legal = new IssuancePlanLine.SubjectIntent(SubjectType.VEHICLE, null,
                "沪A12345", null, null, Map.of("licensePlate", "沪A12345", "vin", "1M8GDM9AXKP042788",
                        "firstRegistrationDate", "2024-01-01"));
        IssuancePlanLine.SubjectIntent illegal = new IssuancePlanLine.SubjectIntent(SubjectType.VEHICLE, null,
                "沪A67890", null, null, Map.of("licensePlate", "沪A67890", "vin", "LSVAA000001234567",
                        "firstRegistrationDate", "2024-01-01"));
        IssuancePlanLine line = new IssuancePlanLine(1, "PROD_AUTO_001", ProductCategory.MAIN, null,
                Money.of(new BigDecimal("200000"), "CNY"), 1, null, PaymentFrequency.ANNUAL, 1,
                List.of(legal, illegal), null);

        RuleDecision decision = service.validate(requestWith(insuredParties(35), line),
                Map.of("PROD_AUTO_001", vehicleRules()));

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.ELIGIBILITY_VIN_INVALID, decision.errorCode());
        assertEquals("LSVAA000001234567", decision.args().get(0), "应报出非法 VIN 原文（第二辆车）");
    }

    @Test
    @DisplayName("非车险段（未声明 vin 必填）不触发 VIN 校验——范围限定（g11-02）")
    void shouldSkipVinCheckForNonVehicleSubject() {
        // 财险产品 required 不含 vin；属性包里出现 vin 键（各险种共用同一 Map 载体）不得被校验
        IssuancePlanLine line = propertyLine(Map.of("propertyAddress", "上海市某路1号", "propertyUsage", "FACTORY",
                "fireProtectionLevel", "A", "propertyValue", 5000000, "vin", "NOT-A-VIN"));

        RuleDecision decision = service.validate(requestWith(insuredParties(35), line),
                Map.of("PROD_PROPERTY_001", propertyRules()));

        assertTrue(decision.passed(), "非车险段不应因 vin 键触发格式校验: " + decision.defaultMessage());
    }

    @Test
    @DisplayName("多标的段 + 产品显式声明不允许多标的 → 拒绝（g11-06）")
    void shouldRejectMultipleSubjectsWhenProductDisallows() {
        RuleDecision decision = service.validate(requestWith(insuredParties(35), twoSubjectPropertyLine()),
                Map.of("PROD_PROPERTY_001", propertyRulesDisallowMultiple()));

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.ELIGIBILITY_MULTIPLE_SUBJECTS_NOT_ALLOWED, decision.errorCode());
        assertEquals(1, decision.lineNo());
        assertEquals(2, decision.args().get(0), "应报出实际标的数");
    }

    @Test
    @DisplayName("多标的段 + 产品显式声明允许多标的 → 通过（g11-06）")
    void shouldAcceptMultipleSubjectsWhenProductAllows() {
        RuleDecision decision = service.validate(requestWith(insuredParties(35), twoSubjectPropertyLine()),
                Map.of("PROD_PROPERTY_001", propertyRulesAllowMultiple()));

        assertTrue(decision.passed(), "预期通过，实际拒绝: " + decision.defaultMessage());
    }

    @Test
    @DisplayName("多标的段 + 产品未声明（null=不限制）→ 放行（三态，g11-06）")
    void shouldAcceptMultipleSubjectsWhenDeclarationAbsent() {
        // propertyRules() 走兼容构造器 → allowMultipleSubjects=null，不得误拒多标的段
        RuleDecision decision = service.validate(requestWith(insuredParties(35), twoSubjectPropertyLine()),
                Map.of("PROD_PROPERTY_001", propertyRules()));

        assertTrue(decision.passed(), "未声明不得误拒多标的段: " + decision.defaultMessage());
    }

    @Test
    @DisplayName("单标的段 + 产品声明不允许多标的 → 不受约束放行（g11-06）")
    void shouldAcceptSingleSubjectWhenProductDisallows() {
        RuleDecision decision = service.validate(
                requestWith(insuredParties(35), propertyLine(completePropertyAttributes())),
                Map.of("PROD_PROPERTY_001", propertyRulesDisallowMultiple()));

        assertTrue(decision.passed(), "单标的段不应触发多标的限制: " + decision.defaultMessage());
    }

    @Test
    @DisplayName("必答告知项未作答被拒绝（g12-02）")
    void shouldRejectWhenRequiredHealthAnswerMissing() {
        // 住院史/手术史均未作答（answers 缺键）——首个必答缺失即拒
        RuleDecision decision = service.validate(
                requestWith(insuredParties(35), planLineWithHealthAnswers(Map.of())),
                Map.of(PRODUCT_ID, medicalRulesWithHealthDeclaration()));

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.ELIGIBILITY_HEALTH_DECLARATION_REQUIRED, decision.errorCode());
        assertEquals(1, decision.lineNo());
        assertEquals("近两年住院史", decision.args().get(0), "应报出缺失的问题名");
    }

    @Test
    @DisplayName("告知项全部作答且无拒保命中时通过（g12-02）")
    void shouldAcceptWhenConfiguredHealthAnswersProvided() {
        RuleDecision decision = service.validate(
                requestWith(insuredParties(35), planLineWithHealthAnswers(Map.of(
                        "HOSPITALIZATION_TWO_YEARS", false,
                        "SURGERY_HISTORY", false,
                        "CHRONIC_DISEASE", false))),
                Map.of(PRODUCT_ID, medicalRulesWithHealthDeclaration()));

        assertTrue(decision.passed(), "答否是有效答案，不得拦截: " + decision.defaultMessage());
    }

    @Test
    @DisplayName("命中拒保处置的告知项答案为肯定时被拒绝（g12-02）")
    void shouldRejectWhenRejectHandlingQuestionAnsweredYes() {
        RuleDecision decision = service.validate(
                requestWith(insuredParties(35), planLineWithHealthAnswers(Map.of(
                        "HOSPITALIZATION_TWO_YEARS", true,
                        "SURGERY_HISTORY", false))),
                Map.of(PRODUCT_ID, medicalRulesWithHealthDeclaration()));

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.ELIGIBILITY_HEALTH_DECLARATION_REJECTED, decision.errorCode());
        assertEquals("近两年住院史", decision.args().get(0), "应报出命中拒保的问题名");
    }

    @Test
    @DisplayName("字符串 \"TRUE\" 同样视为肯定答案（前端报文字面兼容，g12-02）")
    void shouldTreatStringTrueAsAffirmativeAnswer() {
        RuleDecision decision = service.validate(
                requestWith(insuredParties(35), planLineWithHealthAnswers(Map.of(
                        "HOSPITALIZATION_TWO_YEARS", "TRUE",
                        "SURGERY_HISTORY", false))),
                Map.of(PRODUCT_ID, medicalRulesWithHealthDeclaration()));

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.ELIGIBILITY_HEALTH_DECLARATION_REJECTED, decision.errorCode());
    }

    @Test
    @DisplayName("选答项命中拒保处置同样拦截（处置执行与必答无关，g12-02）")
    void shouldRejectWhenOptionalQuestionAnsweredYesWithRejectHandling() {
        RuleDecision decision = service.validate(
                requestWith(insuredParties(35), planLineWithHealthAnswers(Map.of(
                        "HOSPITALIZATION_TWO_YEARS", false,
                        "SURGERY_HISTORY", false,
                        "CHRONIC_DISEASE", true))),
                Map.of(PRODUCT_ID, medicalRulesWithHealthDeclaration()));

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.ELIGIBILITY_HEALTH_DECLARATION_REJECTED, decision.errorCode());
    }

    @Test
    @DisplayName("产品未配置告知项清单时不校验（三态反向判据，g12-02）")
    void shouldSkipHealthDeclarationCheckWhenProductNotConfigured() {
        // medicalRules() 未配置告知项清单：答案全缺也不得拦截（防老产品出单被误拦）
        RuleDecision decision = service.validate(
                requestWith(insuredParties(35), planLineWithHealthAnswers(Map.of())),
                Map.of(PRODUCT_ID, medicalRules()));

        assertTrue(decision.passed(), "未配置告知项的产品不得要求作答: " + decision.defaultMessage());
    }

    @Test
    @DisplayName("未识别的处置码仅做必答校验、不执行处置（g12-02 滚动升级窗口）")
    void shouldOnlyCheckRequiredWhenHandlingUnrecognized() {
        // handling=null 表示对端新增了本域未认识的处置码：答案为肯定也不得拦截
        ProductIssueRules rules = new ProductIssueRules(0, 65, new BigDecimal("1000000"), new BigDecimal("6000000"),
                5, null, List.of(), List.of(), List.of(), 30, 15, null, false,
                List.of(PaymentFrequency.ANNUAL, PaymentFrequency.LUMP_SUM), List.of(1, 5, 10), null, List.of(1, 5, 10),
                null, new BigDecimal("5000000"), false, false, null, List.of(), null,
                List.of(new HealthDeclarationItem(HealthDeclarationQuestion.DRUG_ALLERGY, true, null)));

        RuleDecision decision = service.validate(
                requestWith(insuredParties(35), planLineWithHealthAnswers(Map.of("DRUG_ALLERGY", true))),
                Map.of(PRODUCT_ID, rules));

        assertTrue(decision.passed(), "未识别处置不得拦截: " + decision.defaultMessage());

        // 「只做必答校验」的另一半：未识别的只是处置执行，必答校验不因此豁免——缺答仍拒。
        // （若无此断言，「删掉整段校验改直放行」与本用例同貌，用例对校验存在性零区分力）
        RuleDecision missing = service.validate(
                requestWith(insuredParties(35), planLineWithHealthAnswers(Map.of())),
                Map.of(PRODUCT_ID, rules));

        assertFalse(missing.passed(), "必答校验不因处置未识别而豁免: " + missing.defaultMessage());
        assertEquals(PolicyErrorCode.ELIGIBILITY_HEALTH_DECLARATION_REQUIRED, missing.errorCode());
    }
}
