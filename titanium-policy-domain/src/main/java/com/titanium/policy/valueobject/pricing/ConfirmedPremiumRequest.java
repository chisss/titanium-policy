package com.titanium.policy.valueobject.pricing;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
        Map<String, Object> requestSnapshot,
        List<PremiumAdjustmentInput> underwritingAdjustments,
        String tenantId) {

    public ConfirmedPremiumRequest {
        requestSnapshot = requestSnapshot == null ? Map.of() : Map.copyOf(requestSnapshot);
        underwritingAdjustments = underwritingAdjustments == null ? List.of() : List.copyOf(underwritingAdjustments);
    }
}
