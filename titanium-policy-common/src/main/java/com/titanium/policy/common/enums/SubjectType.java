package com.titanium.policy.common.enums;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 保险标的类型枚举
 * <p>
 * 替代 Subject/ProposalSubject 的 subjectType 裸字符串。标识保险标的的业务分类，
 * 为保单域内部约定，归属本模块 domain 层。
 * </p>
 */
@Getter
public enum SubjectType implements BaseEnum {
    /** 车辆 */
    VEHICLE(1, "VEHICLE", "车辆"),
    /** 房屋 */
    HOUSE(2, "HOUSE", "房屋"),
    /** 人身 */
    PERSON(3, "PERSON", "人身"),
    /** 财产 */
    PROPERTY(4, "PROPERTY", "财产"),
    /** 宠物 */
    PET(5, "PET", "宠物");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    SubjectType(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }

    /**
     * 根据编码反查枚举（统一范式入口，委托 {@link BaseEnum}）
     *
     * @param code 标的类型编码
     * @return 匹配的枚举，未匹配返回 null
     */
    public static SubjectType fromCode(String code) {
        return BaseEnum.fromCode(SubjectType.class, code);
    }
}
