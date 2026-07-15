package com.titanium.policy.common.enums;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 红利领取方式枚举（分红险红利处理）
 * <p>
 * 分红型保单（{@code ParticipationType.PARTICIPATING}）年度红利的领取处置方式。投保人在投保时或红利
 * 派发时选择处置方式，决定红利如何返还或留存。本枚举为 policy 域专属分类，归属本模块 common/enums。
 * </p>
 * <p>
 * 与 product 域 {@code ProductEnum.DividendDistribution}（产品侧配置的红利分配方式）呼应：产品定义
 * 支持的分配方式，保单按投保人选择记录实际领取方式。
 * </p>
 */
@Getter
public enum DividendOption implements BaseEnum {

    /** 现金领取：红利以现金形式给付投保人 */
    CASH(1, "CASH", "现金领取"),

    /** 累积生息：红利留存保单账户按约定利率累积生息 */
    ACCUMULATE(2, "ACCUMULATE", "累积生息"),

    /** 抵缴保费：红利用于抵缴后续应缴保费 */
    OFFSET_PREMIUM(3, "OFFSET_PREMIUM", "抵缴保费"),

    /** 购买交清增额：红利用于购买交清增额保险，增加保额 */
    PAID_UP_ADDITION(4, "PAID_UP_ADDITION", "购买交清增额");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    DividendOption(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }

    /**
     * 按 code 解析红利领取方式，未匹配返回 null。
     *
     * @param code 领取方式编码
     * @return 红利领取方式枚举
     */
    public static DividendOption fromCode(String code) {
        return BaseEnum.fromCode(DividendOption.class, code);
    }

    /**
     * 是否留存保单（累积生息/购买交清增额均留存账户，不直接给付现金）。
     *
     * @return 留存保单返回 {@code true}
     */
    public boolean isRetained() {
        return this == ACCUMULATE || this == PAID_UP_ADDITION;
    }
}
