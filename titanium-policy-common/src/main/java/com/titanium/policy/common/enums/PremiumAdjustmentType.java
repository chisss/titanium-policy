package com.titanium.policy.common.enums;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 核保调整类型枚举（出单确认计算传入 Product 的调整类型值）。
 */
@Getter
public enum PremiumAdjustmentType implements BaseEnum {
    /** 加费比例（按比例上浮保费） */
    SURCHARGE_RATE(1, "SURCHARGE_RATE", "加费比例");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    PremiumAdjustmentType(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
