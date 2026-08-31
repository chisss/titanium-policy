package com.titanium.policy.common.enums;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 缴费周期枚举。
 * <p>
 * 由 domain/valueobject 的 {@code PremiumPaymentCycle} 迁移而来，enumCode/code 值保持不变。
 * </p>
 */
@Getter
public enum PremiumPaymentCycle implements BaseEnum {
    /** 月缴 */
    MONTHLY(1, "MONTHLY", "月缴"),
    /** 季缴 */
    QUARTERLY(2, "QUARTERLY", "季缴"),
    /** 半年缴 */
    SEMI_ANNUALLY(3, "SEMI_ANNUALLY", "半年缴"),
    /** 年缴 */
    ANNUALLY(4, "ANNUALLY", "年缴");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    PremiumPaymentCycle(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
