package com.titanium.policy.common.enums;

import com.titanium.metadata.enums.BaseEnum;
import com.titanium.metadata.enums.Reserved;

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
    @Reserved("2026-10-14 g16-05 零引用登记（未见消费）：待业务核对——接入触发链路或删除；核毕摘标注+删清单行。见 docs/技术文档/枚举零引用清单.md")
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
