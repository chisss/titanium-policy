package com.titanium.policy.common.enums;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 保单域使用的规则引擎裁决。
 * <p>
 * 该枚举隔离规则引擎 API 契约，避免领域层依赖外部模块，同时保留转人工与拒绝的业务差异。
 * </p>
 */
@Getter
public enum RuleEngineDecision implements BaseEnum {
    /** 规则通过，可继续出单。 */
    PASS("PASS"),
    /** 规则拒绝，应同步终止出单。 */
    REJECT("REJECT"),
    /** 转后续核保或人工处理，不应同步拒保。 */
    REFER("REFER");

    private final String code;

    RuleEngineDecision(String code) {
        this.code = code;
    }

    /**
     * 根据业务码反查规则引擎裁决。
     *
     * @param code 规则引擎裁决业务码
     * @return 匹配的裁决，未匹配返回 null
     */
    public static RuleEngineDecision fromCode(String code) {
        return BaseEnum.fromCode(RuleEngineDecision.class, code);
    }
}
