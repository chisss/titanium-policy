package com.titanium.policy.application.orchestration.issuance;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.titanium.common.exception.BusinessException;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.entity.insurance.InsuranceLine;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.entity.policy.InsuredSubject;
import com.titanium.policy.port.ConfirmedPremiumPricingPort;
import com.titanium.policy.valueobject.pricing.ConfirmedPremiumRequest;
import com.titanium.policy.valueobject.pricing.ConfirmedPremiumResult;
import com.titanium.policy.valueobject.pricing.PremiumAdjustmentInput;
import com.titanium.policy.valueobject.pricing.PremiumCalculationReference;

import lombok.RequiredArgsConstructor;

/**
 * 出单险种段确认保费编排。
 * <p>
 * 每个有效险种段独立调用 Product CONFIRM，Policy 只冻结和汇总返回结果，不再解释费率、舍入或
 * 核保调整规则。任一段失败即阻断出单，禁止回退请求中的报价或旧 {@code exactPremium}。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class InsuranceLinePremiumConfirmationService {

    private static final String CONFIRMATION_FAILED = "ISSUANCE_PREMIUM_CONFIRMATION_FAILED";
    private static final String CONFIRMED_STATUS = "CONFIRMED";
    private static final String ISSUANCE_PURPOSE = "ISSUANCE_CONFIRM";
    private static final String SURCHARGE_RATE = "SURCHARGE_RATE";

    private final ConfirmedPremiumPricingPort confirmedPremiumPricingPort;

    /**
     * 确认全部有效险种段保费。
     */
    public ConfirmationSummary confirm(List<InsuranceLine> lines, InsuredPartyList insuredPartyList,
                                       String issuanceReference, String bizNo,
                                       LocalDateTime businessTime, String tenantId,
                                       boolean includeUnderwritingAdjustments) {
        return confirm(lines, insuredPartyList, issuanceReference, bizNo, businessTime, tenantId,
                null, 1, includeUnderwritingAdjustments);
    }

    /**
     * 确认全部有效险种段保费，并携带渠道合同选择所需的业务上下文。
     */
    public ConfirmationSummary confirm(List<InsuranceLine> lines, InsuredPartyList insuredPartyList,
                                       String issuanceReference, String bizNo,
                                       LocalDateTime businessTime, String tenantId,
                                       String channelId, int policyYear,
                                       boolean includeUnderwritingAdjustments) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException("出单缺少可确认保费的险种段", CONFIRMATION_FAILED);
        }
        List<InsuranceLine> confirmedLines = new ArrayList<>();
        List<PremiumCalculationReference> references = new ArrayList<>();
        Money standardTotal = null;
        Money total = null;
        for (InsuranceLine line : lines) {
            if (!line.countsTowardTotalPremium()) {
                confirmedLines.add(line);
                continue;
            }
            ConfirmedPremiumResult result = confirmLine(line, insuredPartyList, issuanceReference, bizNo,
                    businessTime, tenantId, channelId, policyYear, includeUnderwritingAdjustments);
            Money standardPremium = Money.of(result.standardPremium(), result.currency());
            Money confirmedPremium = Money.of(result.totalPremium(), result.currency());
            standardTotal = standardTotal == null ? standardPremium : standardTotal.add(standardPremium);
            total = total == null ? confirmedPremium : total.add(confirmedPremium);
            confirmedLines.add(line.withConfirmedPremium(confirmedPremium));
            references.add(new PremiumCalculationReference(
                    result.calculationId(), result.resultHash(), result.productId(), result.productVersion(),
                    result.pricingPlanVersion(), result.totalPremium(), result.currency(), line.lineId()));
        }
        if (total == null || references.isEmpty()) {
            throw new BusinessException("出单没有可计费的承保险种段", CONFIRMATION_FAILED);
        }
        return new ConfirmationSummary(
                List.copyOf(confirmedLines), standardTotal, total, List.copyOf(references));
    }

    private ConfirmedPremiumResult confirmLine(InsuranceLine line, InsuredPartyList insuredPartyList,
                                               String issuanceReference, String bizNo,
                                               LocalDateTime businessTime, String tenantId,
                                               String channelId, int policyYear,
                                               boolean includeUnderwritingAdjustments) {
        try {
            ConfirmedPremiumRequest request = toRequest(
                    line, insuredPartyList, issuanceReference, bizNo, businessTime, tenantId,
                    channelId, policyYear, includeUnderwritingAdjustments);
            ConfirmedPremiumResult result = confirmedPremiumPricingPort.confirm(request);
            validateResult(line, result);
            return result;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException("险种段确认保费失败: lineNo=" + line.lineNo()
                    + ", productId=" + line.productId(), CONFIRMATION_FAILED, exception);
        }
    }

    private ConfirmedPremiumRequest toRequest(InsuranceLine line, InsuredPartyList insuredPartyList,
                                              String issuanceReference, String bizNo,
                                              LocalDateTime businessTime, String tenantId,
                                              String channelId, int policyYear,
                                              boolean includeUnderwritingAdjustments) {
        if (line.productId() == null || line.productVersion() == null || line.productVersion().isBlank()
                || line.sumInsured() == null || businessTime == null) {
            throw new BusinessException("险种段缺少产品版本、保额或业务时点: lineNo=" + line.lineNo(),
                    CONFIRMATION_FAILED);
        }
        InsuredSubject subject = line.primaryInsured();
        Integer age = integerAttribute(subject, "age");
        String gender = stringAttribute(subject, "gender");
        InsuredPartyList.InsuredInfo insuredParty = findInsuredParty(subject, insuredPartyList);
        if (age == null && insuredParty != null) {
            age = insuredParty.age();
        }
        if ((gender == null || gender.isBlank()) && insuredParty != null && insuredParty.gender() != null) {
            gender = insuredParty.gender().getCode();
        }
        if (age == null || gender == null || gender.isBlank()) {
            throw new BusinessException("险种段缺少被保险人年龄或性别: lineNo=" + line.lineNo(),
                    CONFIRMATION_FAILED);
        }
        int paymentTermYears = line.paymentTerms() != null ? line.paymentTerms().premiumPaymentYears() : 1;
        int paymentPeriods = line.paymentTerms() != null ? line.paymentTerms().totalPeriods() : 1;
        int coverageTermYears = line.coveragePeriod() != null ? line.coveragePeriod().coverageYears() : 0;
        if (coverageTermYears < 1) {
            throw new BusinessException("险种段保障期限必须大于零: lineNo=" + line.lineNo(), CONFIRMATION_FAILED);
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("lineId", line.lineId());
        snapshot.put("lineNo", line.lineNo());
        snapshot.put("productCode", line.productCode());
        snapshot.put("issuanceReference", issuanceReference);
        if (channelId != null && !channelId.isBlank()) {
            snapshot.put("channelId", channelId.trim());
        }
        snapshot.put("policyYear", Math.max(policyYear, 1));
        return new ConfirmedPremiumRequest(
                calculationRequestId(issuanceReference, line), bizNo, line.productId(), line.productVersion(),
                businessTime, line.sumInsured().currency(), line.sumInsured().value(), age, gender,
                Math.max(paymentTermYears, 1), coverageTermYears, Math.max(paymentPeriods, 1), snapshot,
                adjustments(line, includeUnderwritingAdjustments), tenantId);
    }

    private List<PremiumAdjustmentInput> adjustments(InsuranceLine line, boolean includeUnderwritingAdjustments) {
        BigDecimal ratio = line.extraPremiumRatio();
        if (!includeUnderwritingAdjustments || ratio == null || ratio.signum() <= 0) {
            return List.of();
        }
        return List.of(new PremiumAdjustmentInput(
                "UW_SURCHARGE_" + line.lineNo(), SURCHARGE_RATE, ratio, "核保条件加费", null));
    }

    private void validateResult(InsuranceLine line, ConfirmedPremiumResult result) {
        if (result == null || !CONFIRMED_STATUS.equals(result.status())
                || !ISSUANCE_PURPOSE.equals(result.purpose()) || result.calculationId() == null
                || result.resultHash() == null || result.standardPremium() == null || result.totalPremium() == null
                || result.pricingPlanVersion() == null || result.pricingPlanVersion().isBlank()
                || !line.productId().equals(result.productId())
                || !line.productVersion().equals(result.productVersion())) {
            throw new BusinessException("Product 返回的确认保费事实不完整或版本不匹配: lineNo=" + line.lineNo(),
                    CONFIRMATION_FAILED);
        }
    }

    private String calculationRequestId(String issuanceReference, InsuranceLine line) {
        String idempotencyKey = issuanceReference + "|" + line.productId() + "|" + line.lineNo();
        // Product 计算表的幂等键上限为 64 个字符，使用稳定 UUID 保留跨重试幂等性并避免长业务号溢出。
        return "ISSUANCE-" + UUID.nameUUIDFromBytes(idempotencyKey.getBytes(StandardCharsets.UTF_8));
    }

    private Integer integerAttribute(InsuredSubject subject, String key) {
        Object value = attribute(subject, key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.valueOf(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String stringAttribute(InsuredSubject subject, String key) {
        Object value = attribute(subject, key);
        return value != null ? String.valueOf(value) : null;
    }

    private Object attribute(InsuredSubject subject, String key) {
        return subject != null && subject.attributes() != null ? subject.attributes().get(key) : null;
    }

    /**
     * 段内人身标的优先按客户ID匹配参与方；财产/车险无此标的时回退首要被保险人。
     */
    private InsuredPartyList.InsuredInfo findInsuredParty(InsuredSubject subject,
                                                          InsuredPartyList insuredPartyList) {
        if (insuredPartyList == null || insuredPartyList.insuredList() == null
                || insuredPartyList.insuredList().isEmpty()) {
            return null;
        }
        if (subject != null && subject.customerId() != null) {
            for (InsuredPartyList.InsuredInfo insured : insuredPartyList.insuredList()) {
                if (subject.customerId().equals(insured.customerId())) {
                    return insured;
                }
            }
        }
        return insuredPartyList.insuredList().get(0);
    }

    /**
     * 一次出单的逐段确认结果。
     */
    public record ConfirmationSummary(
            List<InsuranceLine> lines,
            Money standardPremium,
            Money totalPremium,
            List<PremiumCalculationReference> calculationReferences) {
    }
}
