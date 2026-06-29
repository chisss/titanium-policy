package com.titanium.policy.query.query;

import java.time.LocalDateTime;

/**
 * 根据多条件查询保单
 */
public record FindPoliciesByMultipleConditionsQuery(
        String policyNo,
        String policyHolderName,
        String insuredName,
        String productCode,
        String status,
        LocalDateTime effectiveDateStart,
        LocalDateTime effectiveDateEnd,
        LocalDateTime expiryDateStart,
        LocalDateTime expiryDateEnd,
        String tenantId,
        int page,
        int size
) {
}
