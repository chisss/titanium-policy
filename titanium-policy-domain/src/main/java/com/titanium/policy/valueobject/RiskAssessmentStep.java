package com.titanium.policy.valueobject;

import lombok.Getter;

/**
 * 风控校验步骤枚举
 * <p>
 * 定义出单流程中的各风控校验环节
 * </p>
 */
@Getter
public enum RiskAssessmentStep {
    /** 黑名单校验 */
    BLACKLIST_CHECK("BLACKLIST_CHECK", "黑名单校验"),
    /** 重复投保校验 */
    DUPLICATE_CHECK("DUPLICATE_CHECK", "重复投保校验"),
    /** 基础自动核保 */
    BASIC_UNDERWRITING("BASIC_UNDERWRITING", "基础自动核保"),
    /** 人工核保 */
    MANUAL_UNDERWRITING("MANUAL_UNDERWRITING", "人工核保"),
    /** 反洗钱校验 */
    AML_CHECK("AML_CHECK", "反洗钱校验"),
    /** 健康告知校验 */
    HEALTH_DECLARATION_CHECK("HEALTH_DECLARATION_CHECK", "健康告知校验");

    private final String code;
    private final String name;

    RiskAssessmentStep(String code, String name) {
        this.code = code;
        this.name = name;
    }
}
