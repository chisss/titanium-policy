package com.titanium.policy.common.enums;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 投保意向单状态编码枚举。
 * <p>
 * 由 domain/valueobject 的 {@code ProposalStatusCode} 迁移而来，enumCode/code 值保持不变。
 * </p>
 */
@Getter
public enum ProposalStatusCode implements BaseEnum {
    /** 草稿 */
    DRAFT(1, "DRAFT", "草稿"),
    /** 已提交 */
    SUBMITTED(2, "SUBMITTED", "已提交"),
    /** 已转投保单 */
    CONVERTED_TO_APPLICATION(3, "CONVERTED_TO_APPLICATION", "已转投保单"),
    /** 作废 */
    VOIDED(4, "VOIDED", "作废");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    ProposalStatusCode(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
