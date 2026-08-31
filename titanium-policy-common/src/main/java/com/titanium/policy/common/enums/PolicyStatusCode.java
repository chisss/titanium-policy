package com.titanium.policy.common.enums;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 保单状态编码枚举。
 * <p>
 * 由 domain/valueobject 的 {@code PolicyStatusCode} 迁移而来（枚举只允许存在于本域
 * common/enums 或 metadata，规约 3.4.2），enumCode/code 值保持不变。
 * </p>
 */
@Getter
public enum PolicyStatusCode implements BaseEnum {
    /** 未生效（对应 metadata PENDING_EFFECTIVE） */
    NOT_EFFECTIVE(1, "NOT_EFFECTIVE", "未生效"),
    /** 生效 */
    EFFECTIVE(2, "EFFECTIVE", "生效"),
    /** 暂停（保全域触发） */
    SUSPENDED(3, "SUSPENDED", "暂停"),
    /** 终止（保全域触发/退保） */
    TERMINATED(4, "TERMINATED", "终止"),
    /** 满期（保险期间届满，定时任务触发，终态） */
    EXPIRED(5, "EXPIRED", "满期"),
    /** 失效/中止（宽限期满未缴费，可经复效恢复，非终态） */
    LAPSED(6, "LAPSED", "失效"),
    /** 已取消（仅未生效保单可取消） */
    CANCELLED(7, "CANCELLED", "已取消");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    PolicyStatusCode(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
