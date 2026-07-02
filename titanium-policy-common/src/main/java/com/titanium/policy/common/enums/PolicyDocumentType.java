package com.titanium.policy.common.enums;

import lombok.Getter;

/**
 * 保单单证类型枚举
 * <p>
 * 标识保单文档（PolicyDocument）的单证业务分类，为保单域内部约定，metadata 无对应枚举，
 * 故归属本模块 domain 层。
 * </p>
 */
@Getter
public enum PolicyDocumentType {
    /** 电子保单 */
    E_POLICY("E_POLICY", "电子保单", "电子保单单证"),
    /** 纸质保单 */
    PAPER_POLICY("PAPER_POLICY", "纸质保单", "纸质保单单证"),
    /** 投保单 */
    PROPOSAL("PROPOSAL", "投保单", "投保单单证"),
    /** 批单 */
    ENDORSEMENT("ENDORSEMENT", "批单", "保全批单单证"),
    /** 发票 */
    INVOICE("INVOICE", "发票", "保费发票单证");

    private final String code;
    private final String name;
    private final String desc;

    PolicyDocumentType(String code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }

    /**
     * 根据编码反查枚举
     *
     * @param code 单证类型编码
     * @return 匹配的枚举，未匹配返回 null
     */
    public static PolicyDocumentType fromCode(String code) {
        for (PolicyDocumentType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
