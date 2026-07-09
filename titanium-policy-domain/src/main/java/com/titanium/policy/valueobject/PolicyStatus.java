package com.titanium.policy.valueobject;

import java.time.LocalDateTime;

import com.titanium.metadata.enums.BaseEnum;
import com.titanium.policy.exception.PolicyStatusTransitionException;

import lombok.Getter;

/**
 * 保单状态值对象
 * <p>
 * 管控保单生命周期状态流转，包含状态编码、状态变更时间、变更原因和操作人ID。
 * 状态机：
 * <pre>
 * NOT_EFFECTIVE ──activate()──► EFFECTIVE ──suspend()──► SUSPENDED
 *       │                          │    ◄──resume()───      │
 *       │                          │                        │
 *       │                     terminate()              terminate()
 *       │                          │                        │
 *       │                          ▼                        ▼
 *       │                     TERMINATED ◄──────────────────┘
 *       │
 *       └──cancel()──► CANCELLED (终态，仅未生效可取消)
 *
 * EFFECTIVE ──expire()──► EXPIRED (定时任务触发，止期到达)
 * </pre>
 * 注：suspend()/resume()/terminate() 均由保全域审批完成后触发，保单域被动执行。
 * 数据变更类保全(投保人/受益人/缴费方式/加减保)不改变保单状态，只更新数据+版本号递增。
 * </p>
 *
 * @param statusCode 状态编码
 * @param statusTime 状态变更时间
 * @param changeReason 变更原因
 * @param operatorId 操作人ID
 */
public record PolicyStatus(StatusCode statusCode, LocalDateTime statusTime, String changeReason, String operatorId) {

    /**
     * 状态流转方法
     *
     * @param newStatusCode 新状态编码
     * @param changeReason 变更原因
     * @param operatorId 操作人ID
     * @return 新的状态值对象
     */
    public PolicyStatus transitionStatus(StatusCode newStatusCode, String changeReason, String operatorId) {
        validateTransition(newStatusCode);
        return new PolicyStatus(newStatusCode, LocalDateTime.now(), changeReason, operatorId);
    }

    /**
     * 判断保单是否处于有效状态（可用于保全域/理赔域校验）
     *
     * @return 是否有效
     */
    public boolean isActive() {
        return this.statusCode == StatusCode.EFFECTIVE;
    }

    /**
     * 判断保单是否处于终态
     *
     * @return 是否终态
     */
    public boolean isTerminal() {
        return this.statusCode == StatusCode.TERMINATED
                || this.statusCode == StatusCode.EXPIRED
                || this.statusCode == StatusCode.CANCELLED;
    }

    /**
     * 状态流转规则校验
     *
     * @param newStatusCode 新状态编码
     */
    private void validateTransition(StatusCode newStatusCode) {
        // 同状态允许（如签发时仍为 NOT_EFFECTIVE）
        if (this.statusCode == newStatusCode) {
            return;
        }
        // NOT_EFFECTIVE → EFFECTIVE（生效）
        if (this.statusCode == StatusCode.NOT_EFFECTIVE && newStatusCode == StatusCode.EFFECTIVE) {
            return;
        }
        // NOT_EFFECTIVE → CANCELLED（取消，仅未生效可取消）
        if (this.statusCode == StatusCode.NOT_EFFECTIVE && newStatusCode == StatusCode.CANCELLED) {
            return;
        }
        // EFFECTIVE → SUSPENDED（暂停，保全域触发）
        if (this.statusCode == StatusCode.EFFECTIVE && newStatusCode == StatusCode.SUSPENDED) {
            return;
        }
        // SUSPENDED → EFFECTIVE（恢复，保全域触发）
        if (this.statusCode == StatusCode.SUSPENDED && newStatusCode == StatusCode.EFFECTIVE) {
            return;
        }
        // EFFECTIVE/SUSPENDED → TERMINATED（终止，保全域触发/退保）
        if ((this.statusCode == StatusCode.EFFECTIVE || this.statusCode == StatusCode.SUSPENDED)
                && newStatusCode == StatusCode.TERMINATED) {
            return;
        }
        // EFFECTIVE → EXPIRED（满期，定时任务触发，止期到达）
        if (this.statusCode == StatusCode.EFFECTIVE && newStatusCode == StatusCode.EXPIRED) {
            return;
        }
        // EFFECTIVE → LAPSED（失效/中止，宽限期满仍未缴费，计费/定时触发）
        if (this.statusCode == StatusCode.EFFECTIVE && newStatusCode == StatusCode.LAPSED) {
            return;
        }
        // LAPSED → EFFECTIVE（复效，保全域触发，补缴保费+重新核保通过后）
        if (this.statusCode == StatusCode.LAPSED && newStatusCode == StatusCode.EFFECTIVE) {
            return;
        }
        // LAPSED → TERMINATED（超过复效期限自动终止 / 失效后退保）
        if (this.statusCode == StatusCode.LAPSED && newStatusCode == StatusCode.TERMINATED) {
            return;
        }
        // 其他状态流转不允许
        throw new PolicyStatusTransitionException(
                "保单", "", this.statusCode.name(), newStatusCode.name());
    }

    /**
     * 状态编码枚举 - 对齐 metadata 层 PolicyEnum.PolicyStatus
     */
    @Getter
    public enum StatusCode implements BaseEnum {
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

        StatusCode(Integer enumCode, String code, String name) {
            this.enumCode = enumCode;
            this.code = code;
            this.name = name;
        }
    }
}
