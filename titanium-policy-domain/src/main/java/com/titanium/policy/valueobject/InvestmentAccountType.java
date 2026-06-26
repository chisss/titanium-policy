package com.titanium.policy.valueobject;

import lombok.Getter;

/**
 * 投资账户类型枚举（投连险/万能险专属）
 * <p>
 * 替代 InvestmentAccount 聚合及其命令/事件的 accountType 裸字符串。
 * 为保单域内部约定，归属本模块 domain 层。
 * </p>
 */
@Getter
public enum InvestmentAccountType {
    /** 投连账户 */
    UNIT_LINKED("UNIT_LINKED", "投连账户"),
    /** 万能账户 */
    UNIVERSAL("UNIVERSAL", "万能账户");

    private final String code;
    private final String name;

    InvestmentAccountType(String code, String name) {
        this.code = code;
        this.name = name;
    }

    /**
     * 根据编码反查枚举
     *
     * @param code 账户类型编码
     * @return 匹配的枚举，未匹配返回 null
     */
    public static InvestmentAccountType fromCode(String code) {
        for (InvestmentAccountType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
