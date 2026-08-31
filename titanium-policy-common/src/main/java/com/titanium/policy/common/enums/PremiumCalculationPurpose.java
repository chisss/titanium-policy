package com.titanium.policy.common.enums;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * Product 确认保费计算用途枚举（出单确认契约的 purpose 值）。
 */
@Getter
public enum PremiumCalculationPurpose implements BaseEnum {
    /** 出单确认（ISSUANCE_CONFIRM 契约） */
    ISSUANCE_CONFIRM(1, "ISSUANCE_CONFIRM", "出单确认");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    PremiumCalculationPurpose(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
