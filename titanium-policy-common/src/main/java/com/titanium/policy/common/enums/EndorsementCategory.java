package com.titanium.policy.common.enums;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 批改大类（按变更对象域划分）
 * <p>
 * 用于批单（Endorsement）的分类标签与读模型聚合，驱动后续按类要素回写的多态分发。
 * 本枚举仅表达"变更对象的范畴"，所有批改均不改变保单状态（状态变更走 4A/4B 状态机）。
 * </p>
 */
@Getter
public enum EndorsementCategory implements BaseEnum {
    /** 当事人类：投保人/被保险人/受益人等主体变更 */
    PARTY(1, "PARTY", "当事人变更"),
    /** 标的类：车辆/不动产/货物等保险标的变更 */
    SUBJECT(2, "SUBJECT", "标的变更"),
    /** 保障责任类：责任增减、限额、免赔、特约 */
    COVERAGE(3, "COVERAGE", "保障责任变更"),
    /** 保额类：加保/减保/保额调整 */
    SUM_INSURED(4, "SUM_INSURED", "保额变更"),
    /** 缴费/财务要素类：缴费方式/频次/追加/减额 */
    PREMIUM_TERMS(5, "PREMIUM_TERMS", "缴费财务要素变更"),
    /** 保险期间类：起止期延长/缩短 */
    PERIOD(6, "PERIOD", "保险期间变更"),
    /** 信息登记类：不影响要素的纯信息更正 */
    INFO(7, "INFO", "信息变更"),
    /** 合同生命周期类：暂停、恢复、复效、终止等状态交易 */
    LIFECYCLE(8, "LIFECYCLE", "合同状态变更");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    EndorsementCategory(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
