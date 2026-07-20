package com.titanium.policy.event;

import java.time.LocalDateTime;
import java.util.List;

import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.entity.PolicyItem;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.valueobject.PolicyNo;
import com.titanium.policy.valueobject.PolicyStatus;

public record PolicyCreatedEvent(String policyId, PolicyNo policyNo, PolicyForm policyForm, String productId,
                                 LocalDateTime effectiveDate, LocalDateTime expiryDate, Money premium, Money sumInsured,
                                 PolicyStatus status, List<PolicyItem> policyItems,
                                 InsuredPartyList insuredPartyList,
                                 InsuranceProductType insuranceType, String tenantId) {
}
