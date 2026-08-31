package com.titanium.policy.common.enums;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 投保单状态编码枚举。
 * <p>
 * 由 domain/valueobject 的 {@code InsuranceStatusCode} 迁移而来，enumCode/code 值保持不变。
 * </p>
 */
@Getter
public enum InsuranceStatusCode implements BaseEnum {
    /** 草稿 */
    DRAFT(1, "DRAFT", "草稿"),
    /** 已提交 */
    SUBMITTED(2, "SUBMITTED", "已提交"),
    /** 核保中 */
    UNDERWRITING(3, "UNDERWRITING", "核保中"),
    /** 核保通过 */
    UNDERWRITING_APPROVED(4, "UNDERWRITING_APPROVED", "核保通过"),
    /** 核保拒绝（终态） */
    UNDERWRITING_REJECTED(5, "UNDERWRITING_REJECTED", "核保拒绝"),
    /** 核保暂缓 */
    UNDERWRITING_SUSPENDED(6, "UNDERWRITING_SUSPENDED", "核保暂缓"),
    /** 已承保（终态） */
    ISSUED(7, "ISSUED", "已承保"),
    /** 作废（终态） */
    VOIDED(8, "VOIDED", "作废");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    InsuranceStatusCode(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
