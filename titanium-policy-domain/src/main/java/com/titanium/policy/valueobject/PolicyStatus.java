package com.titanium.policy.valueobject;

import java.time.LocalDateTime;

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
        // EFFECTIVE → EXPIRED（到期失效，定时任务触发）
        if (this.statusCode == StatusCode.EFFECTIVE && newStatusCode == StatusCode.EXPIRED) {
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
    public enum StatusCode {
        /** 未生效（对应 metadata PENDING_EFFECTIVE） */
        NOT_EFFECTIVE("NOT_EFFECTIVE", "未生效"),
        /** 生效 */
        EFFECTIVE("EFFECTIVE", "生效"),
        /** 暂停（保全域触发） */
        SUSPENDED("SUSPENDED", "暂停"),
        /** 终止（保全域触发/退保） */
        TERMINATED("TERMINATED", "终止"),
        /** 到期失效（定时任务触发） */
        EXPIRED("EXPIRED", "失效"),
        /** 已取消（仅未生效保单可取消） */
        CANCELLED("CANCELLED", "已取消");

        private final String code;
        private final String name;

        StatusCode(String code, String name) {
            this.code = code;
            this.name = name;
        }
    }
}
