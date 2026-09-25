package com.titanium.policy.valueobject.issuance;

import java.util.List;

import com.titanium.metadata.enums.product.ProductEnum;
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.policy.common.enums.RiskAssessmentStep;
import com.titanium.policy.exception.PolicyBusinessRuleException;

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
        ProductEnum.IssuanceMode issuanceMode,
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
                ProductEnum.IssuanceMode.ONE_STEP,
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
                ProductEnum.IssuanceMode.TWO_STEP,
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
                ProductEnum.IssuanceMode.THREE_STEP,
                List.of(RiskAssessmentStep.BLACKLIST_CHECK, RiskAssessmentStep.DUPLICATE_CHECK,
                        RiskAssessmentStep.BASIC_UNDERWRITING, RiskAssessmentStep.MANUAL_UNDERWRITING),
                true,
                false,
                productCode
        );
    }

    /**
     * 按出单模式构建对应配置（产品驱动出单的统一入口）
     * <p>
     * 由产品域配置的出单模式决定走几步出单，取代调用方硬编码具体步数。
     * CUSTOM 模式需规则引擎域配合，暂不支持。
     * </p>
     * <p>
     * 🔴 <b>CUSTOM 分支抛的是携带错误码的领域异常，不是 {@code IllegalArgumentException}</b>：
     * 该分支的可达条件是「产品域把出单模式配成了 CUSTOM」，属<b>业务数据驱动的业务条件</b>，
     * 而非调用方编程错误。此前抛裸 IAE 会被 web 层的 IAE→400 兜底误报成「调用方参数错误」，
     * 且已备好的 {@link PolicyErrorCode#ISSUANCE_MODE_UNSUPPORTED}（20007011）形同虚设。
     * </p>
     *
     * @param mode 出单模式（来自产品配置）
     * @param productCode 产品编码
     * @return 对应模式的出单流程配置
     * @throws IllegalArgumentException                                  出单模式为 {@code null}（调用方漏校验，属编程错误）
     * @throws com.titanium.policy.exception.PolicyBusinessRuleException CUSTOM 模式（业务条件，携带 20007011）
     */
    public static IssuanceProcessConfig forMode(ProductEnum.IssuanceMode mode, String productCode) {
        if (mode == null) {
            throw new IllegalArgumentException("出单模式不能为空");
        }
        return switch (mode) {
            case ONE_STEP -> oneStep(productCode);
            case TWO_STEP -> twoStep(productCode);
            case THREE_STEP -> threeStep(productCode);
            case CUSTOM -> throw new PolicyBusinessRuleException(PolicyErrorCode.ISSUANCE_MODE_UNSUPPORTED,
                    "自定义出单模式(CUSTOM)暂未支持，需规则引擎域配合");
        };
    }
}
