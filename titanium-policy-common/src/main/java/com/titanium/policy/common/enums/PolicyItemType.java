package com.titanium.policy.common.enums;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 保单项目类型枚举
 * <p>
 * 标识保单下保单项（PolicyItem）的业务分类，为保单域内部约定，metadata 无对应枚举，
 * 故归属本模块 domain 层。
 * </p>
 */
@Getter
public enum PolicyItemType implements BaseEnum {
    /** 主险项目 */
    MAIN(1, "MAIN", "主险", "主险保单项"),
    /** 附加险项目 */
    RIDER(2, "RIDER", "附加险", "附加险保单项"),
    /** 责任项目 */
    LIABILITY(3, "LIABILITY", "责任", "保障责任项"),
    /** 费用项目 */
    FEE(4, "FEE", "费用", "费用项");

    private final Integer enumCode;
    private final String  code;
    private final String  name;
    private final String  desc;

    PolicyItemType(Integer enumCode, String code, String name, String desc) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
        this.desc = desc;
    }

    /**
     * 根据编码反查枚举（统一范式入口，委托 {@link BaseEnum}）
     *
     * @param code 项目类型编码
     * @return 匹配的枚举，未匹配返回 null
     */
    public static PolicyItemType fromCode(String code) {
        return BaseEnum.fromCode(PolicyItemType.class, code);
    }
}
