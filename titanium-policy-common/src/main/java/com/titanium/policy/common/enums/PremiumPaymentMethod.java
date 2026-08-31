package com.titanium.policy.common.enums;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 缴费方式枚举。
 * <p>
 * 由 domain/valueobject 的 {@code PremiumPaymentMethod} 迁移而来，enumCode/code 值保持不变。
 * </p>
 */
@Getter
public enum PremiumPaymentMethod implements BaseEnum {
    /** 趸缴 */
    SINGLE_PAYMENT(1, "SINGLE_PAYMENT", "趸缴"),
    /** 期缴 */
    INSTALLMENT_PAYMENT(2, "INSTALLMENT_PAYMENT", "期缴");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    PremiumPaymentMethod(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
