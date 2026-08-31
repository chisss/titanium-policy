package com.titanium.policy.common.enums;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 缴费状态枚举。
 * <p>
 * 由 domain/valueobject 的 {@code PremiumPaymentStatus} 迁移而来，enumCode/code 值保持不变。
 * </p>
 */
@Getter
public enum PremiumPaymentStatus implements BaseEnum {
    /** 未缴费 */
    UNPAID(1, "UNPAID", "未缴费"),
    /** 已缴费 */
    PAID(2, "PAID", "已缴费"),
    /** 部分缴费 */
    PARTIALLY_PAID(3, "PARTIALLY_PAID", "部分缴费"),
    /** 缴费逾期 */
    OVERDUE(4, "OVERDUE", "缴费逾期"),
    /** 缴费完成 */
    COMPLETED(5, "COMPLETED", "缴费完成");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    PremiumPaymentStatus(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
