package com.titanium.policy.valueobject.pricing;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 单个险种段的确认保费请求。
 */
public record ConfirmedPremiumRequest(
        String calculationRequestId,
        String bizNo,
        String productId,
        String productVersion,
        LocalDateTime businessTime,
        String currency,
        BigDecimal sumInsured,
        Integer age,
        String gender,
        Integer paymentTermYears,
        Integer coverageTermYears,
        Integer paymentPeriods,
        ConfirmationContextSnapshot requestSnapshot,
        List<PremiumAdjustmentInput> underwritingAdjustments,
        String tenantId) {

    public ConfirmedPremiumRequest {
        underwritingAdjustments = underwritingAdjustments == null ? List.of() : List.copyOf(underwritingAdjustments);
    }
}
