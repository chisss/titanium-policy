package com.titanium.policy.common.enums;

import com.titanium.metadata.enums.BaseEnum;
import com.titanium.metadata.enums.Reserved;

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
    @Reserved("2026-10-14 g16-05 零引用登记（未见消费）：待业务核对——接入触发链路或删除；核毕摘标注+删清单行。见 docs/技术文档/枚举零引用清单.md")
    PARTIALLY_PAID(3, "PARTIALLY_PAID", "部分缴费"),
    /** 缴费逾期 */
    @Reserved("2026-10-14 g16-05 零引用登记（未见消费）：待业务核对——接入触发链路或删除；核毕摘标注+删清单行。见 docs/技术文档/枚举零引用清单.md")
    OVERDUE(4, "OVERDUE", "缴费逾期"),
    /** 缴费完成 */
    @Reserved("2026-10-14 g16-05 零引用登记（未见消费）：待业务核对——接入触发链路或删除；核毕摘标注+删清单行。见 docs/技术文档/枚举零引用清单.md")
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
