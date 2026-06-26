package com.titanium.policy.valueobject;

import lombok.Getter;

/**
 * 保单数据变更类型枚举
 * <p>
 * 标识保全域触发的保单数据变更类别（不改变保单状态，仅更新数据+版本号递增）。
 * 替代 PolicyDataUpdatedEvent.updateType 裸字符串。本枚举为保单域内部约定，归属本模块 domain 层。
 * </p>
 */
@Getter
public enum PolicyDataUpdateType {
    /** 投保人变更 */
    HOLDER_CHANGE("HOLDER_CHANGE", "投保人变更"),
    /** 受益人变更 */
    BENEFICIARY_CHANGE("BENEFICIARY_CHANGE", "受益人变更"),
    /** 缴费方式变更 */
    PAYMENT_METHOD_CHANGE("PAYMENT_METHOD_CHANGE", "缴费方式变更"),
    /** 加保 */
    COVERAGE_INCREASE("COVERAGE_INCREASE", "加保"),
    /** 减保 */
    COVERAGE_DECREASE("COVERAGE_DECREASE", "减保");

    private final String code;
    private final String name;

    PolicyDataUpdateType(String code, String name) {
        this.code = code;
        this.name = name;
    }

    /**
     * 根据编码反查枚举
     *
     * @param code 变更类型编码
     * @return 匹配的枚举，未匹配返回 null
     */
    public static PolicyDataUpdateType fromCode(String code) {
        for (PolicyDataUpdateType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
