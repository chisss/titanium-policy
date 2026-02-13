package com.titanium.policy.valueobject;

import lombok.Getter;

/**
 * 出单模式枚举
 * <p>
 * 定义保单域支持的三种出单模式，决定承保流程的步骤和参与的聚合根。
 * </p>
 */
@Getter
public enum IssuanceMode {
    /**
     * 一步出单：直接创建保单（跳过意向单+投保单），适用于低风险标准产品
     */
    ONE_STEP("ONE_STEP", "一步出单"),

    /**
     * 两步出单：投保单 → 保单（跳过意向单），适用于中等风险需核保产品
     */
    TWO_STEP("TWO_STEP", "两步出单"),

    /**
     * 三步出单：意向单 → 投保单 → 保单，适用于高风险/团单/大额保单
     */
    THREE_STEP("THREE_STEP", "三步出单");

    private final String code;
    private final String name;

    IssuanceMode(String code, String name) {
        this.code = code;
        this.name = name;
    }
}
