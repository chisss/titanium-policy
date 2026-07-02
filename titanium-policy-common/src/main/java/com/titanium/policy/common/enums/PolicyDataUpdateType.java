package com.titanium.policy.common.enums;

import lombok.Getter;

/**
 * 保单批改类型枚举（分类驱动）
 * <p>
 * 标识保全域触发的保单数据/要素类批改类别。批改不改变保单状态（恒 {@link #changesStatus()}==false，
 * 状态变更走 4A/4B 状态机），仅触发版本号递增与批单留痕，并按 {@link EndorsementCategory} 分类。
 * 以枚举内聚属性（分类/是否核保/是否保费重算/生效日类型）替代处理器内的 switch 类型分支。
 * </p>
 * <p>
 * 与 maintenance 域 MaintenanceType 的非状态类型一一映射（{@link #byMaintenanceType}），状态类
 * （SUSPENSION/RESUMPTION/TERMINATION/REINSTATEMENT）不在此列。本枚举为 policy 域内部约定。
 * </p>
 */
@Getter
public enum PolicyDataUpdateType {
    /** 投保人变更 */
    HOLDER_CHANGE("HOLDER_CHANGE", "投保人变更", EndorsementCategory.PARTY, false, false,
            EndorsementEffectiveType.IMMEDIATE),
    /** 受益人变更 */
    BENEFICIARY_CHANGE("BENEFICIARY_CHANGE", "受益人变更", EndorsementCategory.PARTY, false, false,
            EndorsementEffectiveType.IMMEDIATE),
    /** 缴费方式变更 */
    PAYMENT_METHOD_CHANGE("PAYMENT_METHOD_CHANGE", "缴费方式变更", EndorsementCategory.PREMIUM_TERMS, false, true,
            EndorsementEffectiveType.NEXT_PERIOD),
    /** 加保 */
    COVERAGE_INCREASE("COVERAGE_INCREASE", "加保", EndorsementCategory.SUM_INSURED, true, true,
            EndorsementEffectiveType.SPECIFIED_DATE),
    /** 减保 */
    COVERAGE_DECREASE("COVERAGE_DECREASE", "减保", EndorsementCategory.SUM_INSURED, false, true,
            EndorsementEffectiveType.IMMEDIATE),
    /** 加额缴费 */
    ADDITIONAL_PAYMENT("ADDITIONAL_PAYMENT", "加额缴费", EndorsementCategory.PREMIUM_TERMS, false, true,
            EndorsementEffectiveType.NEXT_PERIOD),
    /** 减额缴费 */
    REDUCTION_PAYMENT("REDUCTION_PAYMENT", "减额缴费", EndorsementCategory.PREMIUM_TERMS, false, true,
            EndorsementEffectiveType.NEXT_PERIOD),
    /** 保单信息变更 */
    POLICY_INFO_CHANGE("POLICY_INFO_CHANGE", "保单信息变更", EndorsementCategory.INFO, false, false,
            EndorsementEffectiveType.IMMEDIATE),
    /** 保险期间变更 */
    POLICY_PERIOD_CHANGE("POLICY_PERIOD_CHANGE", "保险期间变更", EndorsementCategory.PERIOD, true, true,
            EndorsementEffectiveType.SPECIFIED_DATE),
    /** 保额变更 */
    COVERAGE_AMOUNT_CHANGE("COVERAGE_AMOUNT_CHANGE", "保额变更", EndorsementCategory.SUM_INSURED, true, true,
            EndorsementEffectiveType.SPECIFIED_DATE),
    /** 被保人信息变更 */
    INSURED_INFO_CHANGE("INSURED_INFO_CHANGE", "被保人信息变更", EndorsementCategory.PARTY, false, false,
            EndorsementEffectiveType.IMMEDIATE),
    /** 标的变更 */
    SUBJECT_CHANGE("SUBJECT_CHANGE", "标的变更", EndorsementCategory.SUBJECT, true, true,
            EndorsementEffectiveType.SPECIFIED_DATE),
    /** 吸烟状态变更 */
    SMOKING_STATUS_CHANGE("SMOKING_STATUS_CHANGE", "吸烟状态变更", EndorsementCategory.SUBJECT, true, true,
            EndorsementEffectiveType.SPECIFIED_DATE),
    /** 保障责任变更 */
    COVERAGE_CHANGE("COVERAGE_CHANGE", "保障责任变更", EndorsementCategory.COVERAGE, true, true,
            EndorsementEffectiveType.SPECIFIED_DATE);

    private final String                   code;
    private final String                   name;
    private final EndorsementCategory      category;
    private final boolean                  requiresUnderwriting;
    private final boolean                  requiresPremiumRecalc;
    private final EndorsementEffectiveType effectiveType;

    PolicyDataUpdateType(String code, String name, EndorsementCategory category, boolean requiresUnderwriting,
                         boolean requiresPremiumRecalc, EndorsementEffectiveType effectiveType) {
        this.code = code;
        this.name = name;
        this.category = category;
        this.requiresUnderwriting = requiresUnderwriting;
        this.requiresPremiumRecalc = requiresPremiumRecalc;
        this.effectiveType = effectiveType;
    }

    /**
     * 批改是否改变保单状态（恒 false，守恒 4C 范围：批改不碰状态机）
     *
     * @return 恒 false
     */
    public boolean changesStatus() {
        return false;
    }

    /**
     * 是否需要重新核保
     *
     * @return true 表示需要核保
     */
    public boolean needsUnderwriting() {
        return this.requiresUnderwriting;
    }

    /**
     * 是否触发保费重算/补退费
     *
     * @return true 表示需要保费重算
     */
    public boolean needsPremiumRecalc() {
        return this.requiresPremiumRecalc;
    }

    /**
     * 根据编码反查枚举
     *
     * @param code 批改类型编码
     * @return 匹配的枚举，未匹配返回 null
     */
    public static PolicyDataUpdateType fromCode(String code) {
        for (PolicyDataUpdateType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 由 maintenance 域保全类型名映射到批改类型
     * <p>
     * 状态类保全（POLICY_SUSPENSION/POLICY_RESUMPTION/POLICY_TERMINATION/POLICY_REINSTATEMENT）
     * 返回 null（这些走 4A/4B 状态机，不属批改）。投保人变更 maintenance 码为 POLICY_HOLDER_CHANGE，
     * 在此对齐到 HOLDER_CHANGE。
     * </p>
     *
     * @param maintenanceTypeName maintenance 域 MaintenanceType 枚举名
     * @return 对应批改类型；状态类或未知返回 null
     */
    public static PolicyDataUpdateType byMaintenanceType(String maintenanceTypeName) {
        if (maintenanceTypeName == null) {
            return null;
        }
        return switch (maintenanceTypeName) {
            case "POLICY_HOLDER_CHANGE" -> HOLDER_CHANGE;
            case "BENEFICIARY_CHANGE" -> BENEFICIARY_CHANGE;
            case "PAYMENT_METHOD_CHANGE" -> PAYMENT_METHOD_CHANGE;
            case "ADDITIONAL_PAYMENT" -> ADDITIONAL_PAYMENT;
            case "REDUCTION_PAYMENT" -> REDUCTION_PAYMENT;
            case "POLICY_INFO_CHANGE" -> POLICY_INFO_CHANGE;
            case "POLICY_PERIOD_CHANGE" -> POLICY_PERIOD_CHANGE;
            case "COVERAGE_AMOUNT_CHANGE" -> COVERAGE_AMOUNT_CHANGE;
            case "INSURED_INFO_CHANGE" -> INSURED_INFO_CHANGE;
            case "SUBJECT_CHANGE" -> SUBJECT_CHANGE;
            case "SMOKING_STATUS_CHANGE" -> SMOKING_STATUS_CHANGE;
            case "COVERAGE_CHANGE" -> COVERAGE_CHANGE;
            // 状态类保全（SUSPENSION/RESUMPTION/TERMINATION/REINSTATEMENT）不属批改
            default -> null;
        };
    }
}
