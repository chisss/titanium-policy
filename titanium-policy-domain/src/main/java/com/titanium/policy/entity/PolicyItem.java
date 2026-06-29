package com.titanium.policy.entity;

import com.titanium.metadata.valueobject.Money;

public record PolicyItem(String itemId, String coverageId, Coverage coverage, Money sumInsured, Money premium,
                         int deductible, int coinsurance) {
}
