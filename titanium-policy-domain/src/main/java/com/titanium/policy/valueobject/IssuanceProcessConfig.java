package com.titanium.policy.valueobject;

import java.util.List;

/**
 * 出单流程配置值对象
 * <p>
 * 根据出单模式定义各阶段流程步骤，供 IssuanceOrchestrator 编排使用。
 * </p>
 *
 * @param issuanceMode 出单模式
 * @param riskAssessmentSteps 风控校验步骤列表
 * @param requiresManualUnderwriting 是否需要人工核保
 * @param autoActivate 是否自动激活（保障起期到达自动生效）
 * @param productCode 关联产品编码
 */
public record IssuanceProcessConfig(
        IssuanceMode issuanceMode,
        List<RiskAssessmentStep> riskAssessmentSteps,
        boolean requiresManualUnderwriting,
        boolean autoActivate,
        String productCode
) {
    /**
     * 创建一步出单配置
     */
    public static IssuanceProcessConfig oneStep(String productCode) {
        return new IssuanceProcessConfig(
                IssuanceMode.ONE_STEP,
                List.of(RiskAssessmentStep.BLACKLIST_CHECK, RiskAssessmentStep.DUPLICATE_CHECK),
                false,
                true,
                productCode
        );
    }

    /**
     * 创建两步出单配置
     */
    public static IssuanceProcessConfig twoStep(String productCode) {
        return new IssuanceProcessConfig(
                IssuanceMode.TWO_STEP,
                List.of(RiskAssessmentStep.BLACKLIST_CHECK, RiskAssessmentStep.DUPLICATE_CHECK,
                        RiskAssessmentStep.BASIC_UNDERWRITING),
                false,
                false,
                productCode
        );
    }

    /**
     * 创建三步出单配置
     */
    public static IssuanceProcessConfig threeStep(String productCode) {
        return new IssuanceProcessConfig(
                IssuanceMode.THREE_STEP,
                List.of(RiskAssessmentStep.BLACKLIST_CHECK, RiskAssessmentStep.DUPLICATE_CHECK,
                        RiskAssessmentStep.BASIC_UNDERWRITING, RiskAssessmentStep.MANUAL_UNDERWRITING),
                true,
                false,
                productCode
        );
    }
}
