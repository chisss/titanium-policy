package com.titanium.policy.common.enums;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 家庭成员关系枚举
 * <p>
 * 用于家庭险（{@code PolicyForm.FAMILY}）被保险人清单，标识各被保险人与投保人（家庭主投保人）
 * 之间的家庭关系。为保单域内部约定，归属本模块 common 层。
 * </p>
 */
@Getter
public enum FamilyRelation implements BaseEnum {
    /** 本人：家庭主投保人自身 */
    SELF(1, "SELF", "本人"),
    /** 配偶 */
    SPOUSE(2, "SPOUSE", "配偶"),
    /** 子女 */
    CHILD(3, "CHILD", "子女"),
    /** 父母 */
    PARENT(4, "PARENT", "父母");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    FamilyRelation(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }

    /**
     * 根据编码反查枚举（统一范式入口，委托 {@link BaseEnum}）
     *
     * @param code 家庭关系编码
     * @return 匹配的枚举，未匹配返回 null
     */
    public static FamilyRelation fromCode(String code) {
        return BaseEnum.fromCode(FamilyRelation.class, code);
    }
}
