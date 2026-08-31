package com.titanium.policy.application.orchestration.issuance.assembler;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.titanium.policy.common.enums.PremiumAdjustmentReason;
import com.titanium.policy.common.enums.PremiumAdjustmentType;
import com.titanium.policy.entity.insurance.InsuranceLine;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.entity.policy.InsuredSubject;
import com.titanium.policy.valueobject.pricing.ConfirmationContextSnapshot;
import com.titanium.policy.valueobject.pricing.ConfirmedPremiumRequest;
import com.titanium.policy.valueobject.pricing.PremiumAdjustmentInput;

/**
 * 确认保费请求装配器。
 * <p>
 * 把险种段与参与方信息装配为 {@link ConfirmedPremiumRequest}：提取标的信息（年龄/性别，缺失时
 * 回退参与方清单）、计算缴费与保障期限、构造强类型上下文快照与核保调整清单（红线 18：&gt;5 字段
 * 对象构建须经专用 Assembler）。
 * </p>
 */
@Component
public class ConfirmedPremiumRequestAssembler {

    /**
     * 装配确认保费请求。
     *
     * @param line                            险种段
     * @param insuredPartyList                参与方清单（标的信息缺失时回退）
     * @param issuanceReference               出单引用号（幂等键组成）
     * @param bizNo                           业务流水号
     * @param businessTime                    业务时点
     * @param tenantId                        租户ID
     * @param channelId                       渠道ID（可空，渠道合同选择上下文）
     * @param policyYear                      保单年度
     * @param includeUnderwritingAdjustments  是否纳入核保调整
     * @return 确认保费请求
     */
    public ConfirmedPremiumRequest assemble(InsuranceLine line, InsuredPartyList insuredPartyList,
                                            String issuanceReference, String bizNo, LocalDateTime businessTime,
                                            String tenantId, String channelId, int policyYear,
                                            boolean includeUnderwritingAdjustments) {
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
        int paymentTermYears = line.paymentTerms() != null ? line.paymentTerms().premiumPaymentYears() : 1;
        int paymentPeriods = line.paymentTerms() != null ? line.paymentTerms().totalPeriods() : 1;
        int coverageTermYears = line.coveragePeriod() != null ? line.coveragePeriod().coverageYears() : 0;
        return new ConfirmedPremiumRequest(calculationRequestId(issuanceReference, line), bizNo, line.productId(),
                line.productVersion(), businessTime, line.sumInsured().currency(), line.sumInsured().value(), age,
                gender, Math.max(paymentTermYears, 1), coverageTermYears, Math.max(paymentPeriods, 1),
                buildSnapshot(line, issuanceReference, channelId, policyYear),
                adjustments(line, includeUnderwritingAdjustments), tenantId);
    }

    /**
     * 上下文快照：渠道合同选择与保单年度定位所需的结构化上下文（取代松散 Map）。
     */
    private ConfirmationContextSnapshot buildSnapshot(InsuranceLine line, String issuanceReference, String channelId,
                                                      int policyYear) {
        return new ConfirmationContextSnapshot(line.lineId(), line.lineNo(), line.productCode(), issuanceReference,
                channelId != null && !channelId.isBlank() ? channelId.trim() : null, Math.max(policyYear, 1));
    }

    /**
     * 核保调整清单：仅当纳入核保调整且段声明了加费比例时生成。
     */
    private List<PremiumAdjustmentInput> adjustments(InsuranceLine line, boolean includeUnderwritingAdjustments) {
        BigDecimal ratio = line.extraPremiumRatio();
        if (!includeUnderwritingAdjustments || ratio == null || ratio.signum() <= 0) {
            return List.of();
        }
        return List.of(new PremiumAdjustmentInput(
                PremiumAdjustmentReason.UW_SURCHARGE.getCode() + "_" + line.lineNo(),
                PremiumAdjustmentType.SURCHARGE_RATE.getCode(), ratio,
                PremiumAdjustmentReason.UW_SURCHARGE.getName(), null));
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
    private InsuredPartyList.InsuredInfo findInsuredParty(InsuredSubject subject, InsuredPartyList insuredPartyList) {
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
}
