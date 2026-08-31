package com.titanium.policy.common.enums;

import java.time.LocalDateTime;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 年金给付频率枚举
 * <p>
 * 年金保险（{@code InsuranceProductType.ANNUITY}）进入给付期后，按本频率周期性向受益人给付生存年金。
 * 与缴费频率（{@code PremiumPaymentCycle}）语义不同：缴费是保单存续前期投保人向保险人交费，
 * 给付是给付期保险人向被保险人/受益人付款，故为 policy 域内独立约定，归属本模块 common 层。
 * </p>
 */
@Getter
public enum AnnuityPayoutFrequency implements BaseEnum {
    /** 月领：每月给付一次 */
    MONTHLY(1, "MONTHLY", "月领", 1),
    /** 季领：每季度给付一次 */
    QUARTERLY(2, "QUARTERLY", "季领", 3),
    /** 半年领：每半年给付一次 */
    SEMI_ANNUALLY(3, "SEMI_ANNUALLY", "半年领", 6),
    /** 年领：每年给付一次 */
    ANNUALLY(4, "ANNUALLY", "年领", 12);

    private final Integer enumCode;
    private final String  code;
    private final String  name;
    /** 给付间隔月数，用于推算下一给付日 */
    private final int     intervalMonths;

    AnnuityPayoutFrequency(Integer enumCode, String code, String name, int intervalMonths) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
        this.intervalMonths = intervalMonths;
    }

    /**
     * 依据本频率，在给定给付日基础上推算下一给付日。
     *
     * @param current 当前给付日
     * @return 下一给付日
     */
    public LocalDateTime nextPayoutDate(LocalDateTime current) {
        return current.plusMonths(this.intervalMonths);
    }
}
