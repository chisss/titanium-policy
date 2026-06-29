package com.titanium.policy.service;

import java.time.LocalDateTime;

import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.SalesChannel;
import com.titanium.metadata.valueobject.Money;

/**
 * 出单请求
 */
public record IssuanceRequest(
        String productId,
        String productCode,
        PolicyForm policyForm,
        String policyHolderId,
        int insuredCount,
        Money totalPremium,
        LocalDateTime insurancePeriodStart,
        LocalDateTime insurancePeriodEnd,
        SalesChannel channel,
        String tenantId
) {}
