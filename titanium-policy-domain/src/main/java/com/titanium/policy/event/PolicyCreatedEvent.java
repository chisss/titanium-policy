package com.titanium.policy.event;

import java.time.LocalDateTime;
import java.util.List;

import com.titanium.metadata.enums.PolicyEnum.PolicyStatus;
import com.titanium.policy.entity.PolicyItem;
import com.titanium.policy.valueobject.Amount;
import com.titanium.policy.valueobject.PolicyNo;

public record PolicyCreatedEvent(String policyId, PolicyNo policyNo, String customerId, String productId,
        LocalDateTime effectiveDate, LocalDateTime expiryDate, Amount premium, PolicyStatus status,
        List<PolicyItem> policyItems, String tenantId) {
}
