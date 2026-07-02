package com.titanium.policy.common.enums;

import lombok.Getter;

/**
 * 保险标的类型枚举
 * <p>
 * 替代 Subject/ProposalSubject 的 subjectType 裸字符串。标识保险标的的业务分类，
 * 为保单域内部约定，归属本模块 domain 层。
 * </p>
 */
@Getter
public enum SubjectType {
    /** 车辆 */
    VEHICLE("VEHICLE", "车辆"),
    /** 房屋 */
    HOUSE("HOUSE", "房屋"),
    /** 人身 */
    PERSON("PERSON", "人身"),
    /** 财产 */
    PROPERTY("PROPERTY", "财产"),
    /** 宠物 */
    PET("PET", "宠物");

    private final String code;
    private final String name;

    SubjectType(String code, String name) {
        this.code = code;
        this.name = name;
    }

    /**
     * 根据编码反查枚举
     *
     * @param code 标的类型编码
     * @return 匹配的枚举，未匹配返回 null
     */
    public static SubjectType fromCode(String code) {
        for (SubjectType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
