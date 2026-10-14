package com.titanium.policy.common.enums;

import com.titanium.metadata.enums.BaseEnum;
import com.titanium.metadata.enums.Reserved;

import lombok.Getter;

/**
 * 批改生效日类型
 * <p>
 * 决定批单要素变更的生效时点。前三值对齐 maintenance 域 EffectiveTimeType；
 * VALUATION_T1 为投资型险种（按下一估值日净值生效）预留，本阶段先定义不强制使用。
 * </p>
 */
@Getter
public enum EndorsementEffectiveType implements BaseEnum {
    /** 即时生效（批改申请/批准即生效） */
    IMMEDIATE(1, "IMMEDIATE", "即时生效"),
    /** 下一缴费期/保单周年生效 */
    NEXT_PERIOD(2, "NEXT_PERIOD", "下期生效"),
    /** 约定日生效（指定生效日） */
    SPECIFIED_DATE(3, "SPECIFIED_DATE", "约定日生效"),
    /** 下一估值日生效（投资型 T+1 净值，预留） */
    @Reserved("2026-10-14 g16-05 零引用登记（未见消费）：待业务核对——接入触发链路或删除；核毕摘标注+删清单行。见 docs/技术文档/枚举零引用清单.md")
    VALUATION_T1(4, "VALUATION_T1", "下一估值日生效");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    EndorsementEffectiveType(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
