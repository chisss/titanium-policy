package com.titanium.policy.event.insurance;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.titanium.metadata.enums.policy.PolicyForm;

/**
 * 投保单提交核保事件 - Kafka发布到核保域消费
 */
public record InsuranceSubmittedForUnderwritingEvent(
        String insuranceId,
        String insuranceNo,
        String holderId,
        int insuredCount,
        BigDecimal exactPremium,
        String currency,
        LocalDateTime insurancePeriodStart,
        LocalDateTime insurancePeriodEnd,
        List<String> productCodes,
        int underwritingPriority,
        PolicyForm policyForm,
        String tenantId,
        String bizNo
) {

    /**
     * 兼容历史事件构造与回放。
     */
    public InsuranceSubmittedForUnderwritingEvent(String insuranceId, String insuranceNo, String holderId,
                                                   int insuredCount, BigDecimal exactPremium, String currency,
                                                   LocalDateTime insurancePeriodStart,
                                                   LocalDateTime insurancePeriodEnd, List<String> productCodes,
                                                   int underwritingPriority, PolicyForm policyForm, String tenantId) {
        this(insuranceId, insuranceNo, holderId, insuredCount, exactPremium, currency, insurancePeriodStart,
                insurancePeriodEnd, productCodes, underwritingPriority, policyForm, tenantId, null);
    }
}
