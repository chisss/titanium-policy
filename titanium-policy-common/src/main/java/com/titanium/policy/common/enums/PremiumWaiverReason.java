package com.titanium.policy.common.enums;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 保费豁免原因枚举（寿险保费豁免条款）
 * <p>
 * 标识触发保费豁免的约定事件类型。保费豁免是寿险常见附加保障：投保人或被保险人发生约定事件时，
 * 豁免后续应缴保费而保单持续有效、保障不变。本枚举为 policy 域专属分类，归属本模块 common/enums。
 * </p>
 */
@Getter
public enum PremiumWaiverReason implements BaseEnum {

    /** 投保人身故豁免：投保人身故，豁免被保险人后续保费 */
    POLICY_HOLDER_DEATH(1, "POLICY_HOLDER_DEATH", "投保人身故豁免"),

    /** 投保人全残豁免：投保人全残丧失缴费能力，豁免后续保费 */
    POLICY_HOLDER_DISABILITY(2, "POLICY_HOLDER_DISABILITY", "投保人全残豁免"),

    /** 被保险人重疾豁免：被保险人确诊约定重疾，豁免后续保费 */
    INSURED_CRITICAL_ILLNESS(3, "INSURED_CRITICAL_ILLNESS", "被保险人重疾豁免"),

    /** 被保险人全残豁免：被保险人全残，豁免后续保费 */
    INSURED_DISABILITY(4, "INSURED_DISABILITY", "被保险人全残豁免");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    PremiumWaiverReason(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }

    /**
     * 按 code 解析豁免原因，未匹配返回 null。
     *
     * @param code 豁免原因编码
     * @return 豁免原因枚举
     */
    public static PremiumWaiverReason fromCode(String code) {
        return BaseEnum.fromCode(PremiumWaiverReason.class, code);
    }
}
