package com.titanium.policy.entity;

import com.titanium.policy.valueobject.Amount;

public record PolicyItem(String itemId, String coverageId, Coverage coverage, Amount sumInsured, Amount premium,
                         int deductible, int coinsurance) {
}
