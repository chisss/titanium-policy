package com.titanium.policy.common.enums;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 保单保费收讫状态枚举（保单域专属）
 * <p>
 * 表达<b>保单维度</b>对保费收取进度的汇总判定，是保单能否生效的依据之一。
 * </p>
 * <p>
 * 🔴 与 {@code BillingEnum.PaymentStatus} 的区别：后者是<b>单笔缴费流水</b>的处理状态
 * （待缴费/缴费中/成功/失败/逾期），面向支付通道；本枚举是<b>保单应收 vs 实收</b>的聚合状态，
 * 含「部分收讫」这一流水状态无法表达的语义（分期首期已收、后续未收）。二者不可互相替代。
 * </p>
 */
@Getter
public enum PremiumCollectionStatus implements BaseEnum {

    /** 未收讫（账单已开立，尚无实收） */
    UNCOLLECTED(1, "UNCOLLECTED", "未收讫", "账单已开立但尚未收到保费"),
    /** 部分收讫（分期缴费已收部分期数，或实收小于应收） */
    PARTIALLY_COLLECTED(2, "PARTIALLY_COLLECTED", "部分收讫", "实收金额小于应收金额"),
    /** 已收讫（实收达到应收，保单可生效） */
    COLLECTED(3, "COLLECTED", "已收讫", "实收金额达到应收金额"),
    /** 后付（先享后付方式，账单挂账待付，保单已生效） */
    DEFERRED(4, "DEFERRED", "后付", "先享后付：保障先行、账单后付"),
    /** 逾期（宽限期满仍未收讫，触发保单失效流程） */
    OVERDUE(5, "OVERDUE", "逾期", "宽限期满未收讫，触发失效流程");

    private final Integer enumCode;
    private final String  code;
    private final String  name;
    private final String  desc;

    PremiumCollectionStatus(Integer enumCode, String code, String name, String desc) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
        this.desc = desc;
    }

    /**
     * 按 code（枚举名称）反查收讫状态，未匹配返回 null。
     *
     * @param code 收讫状态编码
     * @return 匹配的枚举，未匹配返回 null
     */
    public static PremiumCollectionStatus fromCode(String code) {
        return BaseEnum.fromCode(PremiumCollectionStatus.class, code);
    }

    /**
     * 该状态下保单是否满足「保费条件」可生效。
     * <p>
     * 已收讫、或先享后付挂账，均满足；未收讫/部分收讫/逾期不满足。
     * </p>
     *
     * @return 满足生效的保费条件返回 {@code true}
     */
    public boolean allowsActivation() {
        return this == COLLECTED || this == DEFERRED;
    }
}
