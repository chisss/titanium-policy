package com.titanium.policy.event.insurance;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;

/**
 * 投保单创建事件
 */
public record InsuranceCreatedEvent(
        String insuranceId,
        String insuranceNo,
        String proposalId,
        PolicyForm policyForm,
        String holderId,
        int insuredCount,
        BigDecimal exactPremium,
        LocalDateTime insurancePeriodStart,
        LocalDateTime insurancePeriodEnd,
        List<String> productCodes,
        int underwritingPriority,
        InsuranceProductType insuranceType,
        LocalDateTime createTime,
        String tenantId
) {}
