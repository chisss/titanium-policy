package com.titanium.policy.service.impl;

import static com.titanium.metadata.errorcode.PolicyErrorCode.ELIGIBILITY_AGE_BELOW_MIN;
import static com.titanium.metadata.errorcode.PolicyErrorCode.ELIGIBILITY_AGE_EXCEEDS_MAX;
import static com.titanium.metadata.errorcode.PolicyErrorCode.ELIGIBILITY_BENEFICIARY_RATIO_INVALID;
import static com.titanium.metadata.errorcode.PolicyErrorCode.ELIGIBILITY_BENEFICIARY_REQUIRED;
import static com.titanium.metadata.errorcode.PolicyErrorCode.ELIGIBILITY_COVERAGE_PERIOD_NOT_ALLOWED;
import static com.titanium.metadata.errorcode.PolicyErrorCode.ELIGIBILITY_HOLDER_REQUIRED;
import static com.titanium.metadata.errorcode.PolicyErrorCode.ELIGIBILITY_INSURED_COUNT_BELOW_MIN;
import static com.titanium.metadata.errorcode.PolicyErrorCode.ELIGIBILITY_INSURED_COUNT_EXCEEDS_MAX;
import static com.titanium.metadata.errorcode.PolicyErrorCode.ELIGIBILITY_INSURED_REQUIRED;
import static com.titanium.metadata.errorcode.PolicyErrorCode.ELIGIBILITY_OCCUPATION_FORBIDDEN;
import static com.titanium.metadata.errorcode.PolicyErrorCode.ELIGIBILITY_PAYMENT_FREQUENCY_NOT_ALLOWED;
import static com.titanium.metadata.errorcode.PolicyErrorCode.ELIGIBILITY_PAYMENT_TERM_NOT_ALLOWED;
import static com.titanium.metadata.errorcode.PolicyErrorCode.ELIGIBILITY_REGION_FORBIDDEN;
import static com.titanium.metadata.errorcode.PolicyErrorCode.ELIGIBILITY_REGION_NOT_ALLOWED;
import static com.titanium.metadata.errorcode.PolicyErrorCode.ELIGIBILITY_SUM_INSURED_BELOW_MIN;
import static com.titanium.metadata.errorcode.PolicyErrorCode.ELIGIBILITY_SUM_INSURED_EXCEEDS_MAX;
import static com.titanium.metadata.errorcode.PolicyErrorCode.ELIGIBILITY_SUM_INSURED_REQUIRED;
import static com.titanium.metadata.errorcode.PolicyErrorCode.ELIGIBILITY_SUBJECT_ATTRIBUTE_MISSING;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.service.IssuanceEligibilityDomainService;
import com.titanium.policy.valueobject.IssuancePlanLine;
import com.titanium.policy.valueobject.IssuanceRequest;
import com.titanium.policy.valueobject.RuleDecision;
import com.titanium.policy.valueobject.product.ProductIssueRules;

/**
 * 投保要素校验领域服务实现（纯领域逻辑，可脱离 Spring 用 {@code new} 直测）
 * <p>
 * 无 Port / 仓储 / 基础设施依赖，入参出参仅值对象。标注 {@code @Service} 仅为便于容器注入。
 * </p>
 * <p>
 * <b>三条实现原则</b>：
 * </p>
 * <ol>
 *   <li><b>区间与清单判定内聚到 {@link ProductIssueRules}</b>（充血）——本类不手写
 *       {@code minAge != null && age < minAge} 这类比较，只负责「按序编排规则 + 违反时给错误码」；</li>
 *   <li><b>只给错误码与参数，不拼中文句子</b>（红线 15）——多语言文案由边界层按
 *       {@code Accept-Language} 经 {@code MessageSource} 渲染；</li>
 *   <li><b>取值逻辑内聚到 {@link IssuancePlanLine}</b>——标的属性提取由方案行自己负责，
 *       本类不重复实现 Map 取值与类型收窄。</li>
 * </ol>
 */
@Service
public class IssuanceEligibilityDomainServiceImpl implements IssuanceEligibilityDomainService {

    /** 标的属性中的年龄键（与出单装配器约定一致） */
    private static final String ATTR_AGE = "age";
    /** 标的属性中的职业类别键 */
    private static final String ATTR_OCCUPATION = "occupation";
    /** 标的属性中的地域键 */
    private static final String ATTR_REGION = "region";

    @Override
    public RuleDecision validate(IssuanceRequest request, Map<String, ProductIssueRules> rulesByProduct) {
        RuleDecision documentDecision = validateDocumentLevel(request);
        if (!documentDecision.passed() || request.planLines() == null) {
            return documentDecision;
        }
        for (IssuancePlanLine line : request.planLines()) {
            ProductIssueRules rules = rulesByProduct != null ? rulesByProduct.get(line.productId()) : null;
            if (rules == null) {
                continue;
            }
            RuleDecision lineDecision = validateLine(request, line, rules);
            if (!lineDecision.passed()) {
                return lineDecision;
            }
        }
        return RuleDecision.accepted();
    }

    // ==================== 单据级规则（跨段共用要素） ====================

    /**
     * 单据级规则：投保人必填、至少一名被保险人、受益份额守恒。
     */
    private RuleDecision validateDocumentLevel(IssuanceRequest request) {
        if (request.holderCustomerId() == null || request.holderCustomerId().isBlank()) {
            return RuleDecision.rejected(ELIGIBILITY_HOLDER_REQUIRED);
        }
        if (request.insuredCount() == 0) {
            return RuleDecision.rejected(ELIGIBILITY_INSURED_REQUIRED);
        }
        InsuredPartyList partyList = request.insuredPartyList();
        if (partyList != null && !partyList.isBeneficiaryRatioValid()) {
            return RuleDecision.rejected(ELIGIBILITY_BENEFICIARY_RATIO_INVALID);
        }
        return RuleDecision.accepted();
    }

    // ==================== 段级规则（各段独立要素） ====================

    /**
     * 段级规则按序校验，返回首个违反项（快速失败，便于调用方定位具体段与规则）。
     */
    private RuleDecision validateLine(IssuanceRequest request, IssuancePlanLine line, ProductIssueRules rules) {
        RuleDecision age = validateAge(request, line, rules);
        if (!age.passed()) {
            return age;
        }
        RuleDecision sumInsured = validateSumInsured(line, rules);
        if (!sumInsured.passed()) {
            return sumInsured;
        }
        RuleDecision occupation = validateOccupation(line, rules);
        if (!occupation.passed()) {
            return occupation;
        }
        RuleDecision region = validateRegion(line, rules);
        if (!region.passed()) {
            return region;
        }
        RuleDecision subject = validateSubjectAttributes(line, rules);
        if (!subject.passed()) {
            return subject;
        }
        RuleDecision insuredCount = validateInsuredCount(request, line, rules);
        if (!insuredCount.passed()) {
            return insuredCount;
        }
        RuleDecision period = validateCoveragePeriod(line, rules);
        if (!period.passed()) {
            return period;
        }
        RuleDecision payment = validatePaymentTerms(line, rules);
        if (!payment.passed()) {
            return payment;
        }
        return validateBeneficiaryRequirement(request, line, rules);
    }

    /**
     * 按产品模板的 JSON Schema 校验物类标的必填属性。属性包只在受理边界校验一次，
     * 后续险种段与保单快照沿用同一份已验证数据。
     */
    private RuleDecision validateSubjectAttributes(IssuancePlanLine line, ProductIssueRules rules) {
        if (line.subjects() == null || line.subjects().isEmpty()
                || rules.requiredSubjectAttributes() == null || rules.requiredSubjectAttributes().isEmpty()) {
            return RuleDecision.accepted();
        }
        for (IssuancePlanLine.SubjectIntent subject : line.subjects()) {
            for (String attribute : rules.requiredSubjectAttributes()) {
                String value = subject.attributeText(attribute);
                if (value == null || value.isBlank()) {
                    return RuleDecision.rejectedAtLine(ELIGIBILITY_SUBJECT_ATTRIBUTE_MISSING, line.lineNo(), attribute);
                }
            }
        }
        return RuleDecision.accepted();
    }

    /**
     * 投保年龄须落在产品允许区间（各被保险人逐一校验，任一超限即拒）。
     */
    private RuleDecision validateAge(IssuanceRequest request, IssuancePlanLine line, ProductIssueRules rules) {
        for (Integer age : insuredAges(request, line)) {
            if (rules.allowsAge(age)) {
                continue;
            }
            return rules.isAgeBelowMin(age)
                    ? RuleDecision.rejectedAtLine(ELIGIBILITY_AGE_BELOW_MIN, line.lineNo(), age, rules.minAge())
                    : RuleDecision.rejectedAtLine(ELIGIBILITY_AGE_EXCEEDS_MAX, line.lineNo(), age, rules.maxAge());
        }
        return RuleDecision.accepted();
    }

    /**
     * 保额须落在产品允许区间。
     */
    private RuleDecision validateSumInsured(IssuancePlanLine line, ProductIssueRules rules) {
        Money sumInsured = line.sumInsured();
        if (sumInsured == null) {
            return RuleDecision.rejectedAtLine(ELIGIBILITY_SUM_INSURED_REQUIRED, line.lineNo());
        }
        if (rules.allowsSumInsured(sumInsured.value())) {
            return RuleDecision.accepted();
        }
        return rules.isSumInsuredBelowMin(sumInsured.value())
                ? RuleDecision.rejectedAtLine(ELIGIBILITY_SUM_INSURED_BELOW_MIN, line.lineNo(), sumInsured.value(),
                        rules.minSumInsured())
                : RuleDecision.rejectedAtLine(ELIGIBILITY_SUM_INSURED_EXCEEDS_MAX, line.lineNo(), sumInsured.value(),
                        rules.maxSumInsured());
    }

    /**
     * 职业不得在产品禁保清单内。
     */
    private RuleDecision validateOccupation(IssuancePlanLine line, ProductIssueRules rules) {
        for (String occupation : line.subjectAttributeTexts(ATTR_OCCUPATION)) {
            if (!rules.allowsOccupation(occupation)) {
                return RuleDecision.rejectedAtLine(ELIGIBILITY_OCCUPATION_FORBIDDEN, line.lineNo(), occupation);
            }
        }
        return RuleDecision.accepted();
    }

    /**
     * 地域校验：不在禁止清单内，且（若配置了允许清单）须在允许清单内。
     */
    private RuleDecision validateRegion(IssuancePlanLine line, ProductIssueRules rules) {
        for (String region : line.subjectAttributeTexts(ATTR_REGION)) {
            if (rules.allowsRegion(region)) {
                continue;
            }
            return rules.isRegionForbidden(region)
                    ? RuleDecision.rejectedAtLine(ELIGIBILITY_REGION_FORBIDDEN, line.lineNo(), region)
                    : RuleDecision.rejectedAtLine(ELIGIBILITY_REGION_NOT_ALLOWED, line.lineNo(), region);
        }
        return RuleDecision.accepted();
    }

    /**
     * 被保险人数须落在产品允许区间（团险有最小参保人数要求）。
     */
    private RuleDecision validateInsuredCount(IssuanceRequest request, IssuancePlanLine line,
                                              ProductIssueRules rules) {
        int count = request.insuredCount();
        if (rules.allowsInsuredCount(count)) {
            return RuleDecision.accepted();
        }
        return rules.isInsuredCountBelowMin(count)
                ? RuleDecision.rejectedAtLine(ELIGIBILITY_INSURED_COUNT_BELOW_MIN, line.lineNo(), count,
                        rules.minGroupSize())
                : RuleDecision.rejectedAtLine(ELIGIBILITY_INSURED_COUNT_EXCEEDS_MAX, line.lineNo(), count,
                        rules.maxInsuredCount());
    }

    /**
     * 保障期限须在产品固定期限选项内。
     */
    private RuleDecision validateCoveragePeriod(IssuancePlanLine line, ProductIssueRules rules) {
        return rules.allowsCoveragePeriod(line.coveragePeriodValue())
                ? RuleDecision.accepted()
                : RuleDecision.rejectedAtLine(ELIGIBILITY_COVERAGE_PERIOD_NOT_ALLOWED, line.lineNo(),
                        line.coveragePeriodValue(), rules.fixedTermOptions());
    }

    /**
     * 缴费频率与缴费年数须在产品允许集内。
     */
    private RuleDecision validatePaymentTerms(IssuancePlanLine line, ProductIssueRules rules) {
        if (!rules.allowsFrequency(line.paymentFrequency())) {
            return RuleDecision.rejectedAtLine(ELIGIBILITY_PAYMENT_FREQUENCY_NOT_ALLOWED, line.lineNo(),
                    line.paymentFrequency().getName());
        }
        return rules.allowsPaymentTerm(line.premiumPaymentYears())
                ? RuleDecision.accepted()
                : RuleDecision.rejectedAtLine(ELIGIBILITY_PAYMENT_TERM_NOT_ALLOWED, line.lineNo(),
                        line.premiumPaymentYears(), rules.allowedPaymentTerms());
    }

    /**
     * 产品要求必须指定受益人时，校验受益人清单非空。
     */
    private RuleDecision validateBeneficiaryRequirement(IssuanceRequest request, IssuancePlanLine line,
                                                        ProductIssueRules rules) {
        if (!rules.beneficiaryRequired()) {
            return RuleDecision.accepted();
        }
        InsuredPartyList partyList = request.insuredPartyList();
        boolean hasBeneficiary = partyList != null && partyList.beneficiaryList() != null
                && !partyList.beneficiaryList().isEmpty();
        return hasBeneficiary
                ? RuleDecision.accepted()
                : RuleDecision.rejectedAtLine(ELIGIBILITY_BENEFICIARY_REQUIRED, line.lineNo());
    }

    /**
     * 取该段各被保险人年龄：优先取段内标的属性，回退参与方清单。
     */
    private List<Integer> insuredAges(IssuanceRequest request, IssuancePlanLine line) {
        List<Integer> fromSubjects = line.subjectAttributeInts(ATTR_AGE);
        return fromSubjects.isEmpty() ? request.insuredAges() : fromSubjects;
    }
}
