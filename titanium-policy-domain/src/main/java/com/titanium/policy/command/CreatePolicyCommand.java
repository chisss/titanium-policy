package com.titanium.policy.command;

import java.time.LocalDateTime;
import java.util.List;

import com.titanium.policy.entity.PolicyItem;
import com.titanium.policy.valueobject.Amount;
import com.titanium.policy.valueobject.PolicyNo;

public record CreatePolicyCommand(String policyId, PolicyNo policyNo, String customerId, String productId,
        LocalDateTime effectiveDate, LocalDateTime expiryDate, Amount premium, List<PolicyItem> policyItems,
        String tenantId) {
}
