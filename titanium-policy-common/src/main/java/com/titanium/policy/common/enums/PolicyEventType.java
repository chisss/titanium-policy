package com.titanium.policy.common.enums;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 保单领域事件类型枚举
 * <p>
 * 替代原 PolicyConstants.EventType 字符串常量，标识保单生命周期内对外发布的领域事件类型。
 * 本枚举为保单域内部约定，不跨微服务复用，故归属本模块 domain 层。
 * </p>
 */
@Getter
public enum PolicyEventType implements BaseEnum {
    /** 保单创建 */
    POLICY_CREATED(1, "POLICY_CREATED", "保单创建"),
    /** 保单激活 */
    POLICY_ACTIVATED(2, "POLICY_ACTIVATED", "保单激活"),
    /** 保单过期 */
    POLICY_EXPIRED(3, "POLICY_EXPIRED", "保单过期"),
    /** 保单取消 */
    POLICY_CANCELLED(4, "POLICY_CANCELLED", "保单取消");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    PolicyEventType(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }

    /**
     * 根据编码反查枚举（统一范式入口，委托 {@link BaseEnum}）
     *
     * @param code 事件类型编码
     * @return 匹配的枚举，未匹配返回 null
     */
    public static PolicyEventType fromCode(String code) {
        return BaseEnum.fromCode(PolicyEventType.class, code);
    }
}
