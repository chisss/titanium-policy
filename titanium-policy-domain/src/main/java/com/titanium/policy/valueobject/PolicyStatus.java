package com.titanium.policy.valueobject;

import java.time.LocalDateTime;

import lombok.Getter;

/**
 * 保单状态值对象
 * <p>
 * 管控保单生命周期状态流转，包含状态编码、状态变更时间、变更原因和操作人ID
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
     * <p>
     * 执行状态流转校验，确保状态变更符合业务规则
     * </p>
     *
     * @param newStatusCode 新状态编码
     * @param changeReason 变更原因
     * @param operatorId 操作人ID
     * @return 新的状态值对象
     */
    public PolicyStatus transitionStatus(StatusCode newStatusCode, String changeReason, String operatorId) {
        // 状态流转规则校验
        validateTransition(newStatusCode);
        return new PolicyStatus(newStatusCode, LocalDateTime.now(), changeReason, operatorId);
    }

    /**
     * 状态流转规则校验
     *
     * @param newStatusCode 新状态编码
     */
    private void validateTransition(StatusCode newStatusCode) {
        // 未生效状态可以转为生效
        if (this.statusCode == StatusCode.NOT_EFFECTIVE && newStatusCode == StatusCode.EFFECTIVE) {
            return;
        }
        // 生效状态可以转为暂停
        if (this.statusCode == StatusCode.EFFECTIVE && newStatusCode == StatusCode.SUSPENDED) {
            return;
        }
        // 暂停状态可以转为生效
        if (this.statusCode == StatusCode.SUSPENDED && newStatusCode == StatusCode.EFFECTIVE) {
            return;
        }
        // 生效或暂停状态可以转为终止
        if ((this.statusCode == StatusCode.EFFECTIVE || this.statusCode == StatusCode.SUSPENDED)
                && newStatusCode == StatusCode.TERMINATED) {
            return;
        }
        // 生效或暂停状态可以转为失效
        if ((this.statusCode == StatusCode.EFFECTIVE || this.statusCode == StatusCode.SUSPENDED)
                && newStatusCode == StatusCode.EXPIRED) {
            return;
        }
        // 任何状态都可以转为已批改
        if (newStatusCode == StatusCode.ENDORSED) {
            return;
        }
        // 其他状态流转不允许
        throw new IllegalArgumentException(
                String.format("Invalid status transition from %s to %s", this.statusCode, newStatusCode));
    }

    /**
     * 状态编码枚举
     */
    @Getter
    public enum StatusCode {
        /**
         * 未生效
         */
        NOT_EFFECTIVE("NOT_EFFECTIVE", "未生效"),
        /**
         * 生效
         */
        EFFECTIVE("EFFECTIVE", "生效"),
        /**
         * 暂停
         */
        SUSPENDED("SUSPENDED", "暂停"),
        /**
         * 终止
         */
        TERMINATED("TERMINATED", "终止"),
        /**
         * 失效
         */
        EXPIRED("EXPIRED", "失效"),
        /**
         * 已批改
         */
        ENDORSED("ENDORSED", "已批改");

        private final String code;
        private final String name;

        StatusCode(String code, String name) {
            this.code = code;
            this.name = name;
        }
    }
}
