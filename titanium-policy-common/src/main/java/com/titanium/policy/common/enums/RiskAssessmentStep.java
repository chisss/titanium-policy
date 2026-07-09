package com.titanium.policy.common.enums;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 风控校验步骤枚举
 * <p>
 * 定义出单流程中的各风控校验环节
 * </p>
 */
@Getter
public enum RiskAssessmentStep implements BaseEnum {
    /** 黑名单校验 */
    BLACKLIST_CHECK(1, "BLACKLIST_CHECK", "黑名单校验"),
    /** 重复投保校验 */
    DUPLICATE_CHECK(2, "DUPLICATE_CHECK", "重复投保校验"),
    /** 基础自动核保 */
    BASIC_UNDERWRITING(3, "BASIC_UNDERWRITING", "基础自动核保"),
    /** 人工核保 */
    MANUAL_UNDERWRITING(4, "MANUAL_UNDERWRITING", "人工核保"),
    /** 反洗钱校验 */
    AML_CHECK(5, "AML_CHECK", "反洗钱校验"),
    /** 健康告知校验 */
    HEALTH_DECLARATION_CHECK(6, "HEALTH_DECLARATION_CHECK", "健康告知校验");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    RiskAssessmentStep(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
