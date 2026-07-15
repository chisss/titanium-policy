package com.titanium.policy.common.enums;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 年金给付计划状态枚举
 * <p>
 * 标识年金给付计划的生命周期：启动给付期后进入 {@code PAYING}，逐期给付；给付满约定期数或
 * 达到终止条件后进入 {@code COMPLETED}；保单终止/退保等外部原因导致给付中止则为 {@code STOPPED}。
 * </p>
 */
@Getter
public enum AnnuityPayoutStatus implements BaseEnum {
    /** 给付中：给付期已启动，按频率逐期给付生存年金 */
    PAYING(1, "PAYING", "给付中"),
    /** 已完成：给付期正常结束（给付满约定期数） */
    COMPLETED(2, "COMPLETED", "已完成"),
    /** 已中止：因保单终止/退保等外部原因提前停止给付 */
    STOPPED(3, "STOPPED", "已中止");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    AnnuityPayoutStatus(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
