package com.titanium.policy.service;

import java.time.LocalDateTime;

import com.titanium.policy.valueobject.Amount;

/**
 * 出单请求
 */
public record IssuanceRequest(
        String productId,
        String productCode,
        String policyForm,
        String policyHolderId,
        int insuredCount,
        Amount totalPremium,
        LocalDateTime insurancePeriodStart,
        LocalDateTime insurancePeriodEnd,
        String channel,
        String tenantId
) {}
