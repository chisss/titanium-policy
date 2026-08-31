package com.titanium.policy.common.enums;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 核保调整原因枚举。
 * <p>
 * 调整编码以本枚举 code 为前缀拼接险种段序号（如 {@code UW_SURCHARGE_2}），名称作为调整原因
 * 传入 Product 与账单域，避免魔法字符串散落（规约红线 17）。
 * </p>
 */
@Getter
public enum PremiumAdjustmentReason implements BaseEnum {
    /** 核保条件加费 */
    UW_SURCHARGE(1, "UW_SURCHARGE", "核保条件加费");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    PremiumAdjustmentReason(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
