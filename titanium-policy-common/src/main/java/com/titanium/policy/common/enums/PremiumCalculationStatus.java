package com.titanium.policy.common.enums;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * Product 确认保费计算状态枚举（出单确认契约的状态值）。
 */
@Getter
public enum PremiumCalculationStatus implements BaseEnum {
    /** 已确认（Product 已持久化确认计算事实） */
    CONFIRMED(1, "CONFIRMED", "已确认");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    PremiumCalculationStatus(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
