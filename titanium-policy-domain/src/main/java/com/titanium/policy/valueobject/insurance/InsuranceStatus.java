package com.titanium.policy.valueobject.insurance;

import java.time.LocalDateTime;

import lombok.Getter;

/**
 * 投保单状态值对象
 * <p>
 * 管控投保单生命周期状态流转，包含状态编码、状态变更时间和变更原因
 * </p>
 *
 * @param statusCode 状态编码
 * @param statusTime 状态变更时间
 * @param changeReason 变更原因
 */
public record InsuranceStatus(StatusCode statusCode, LocalDateTime statusTime, String changeReason) {

    /**
     * 状态流转方法
     * <p>
     * 执行状态流转校验，确保状态变更符合业务规则
     * </p>
     *
     * @param newStatusCode 新状态编码
     * @param changeReason 变更原因
     * @return 新的状态值对象
     */
    public InsuranceStatus transitionStatus(StatusCode newStatusCode, String changeReason) {
        // 状态流转规则校验
        validateTransition(newStatusCode);
        return new InsuranceStatus(newStatusCode, LocalDateTime.now(), changeReason);
    }

    /**
     * 状态流转规则校验
     *
     * @param newStatusCode 新状态编码
     */
    private void validateTransition(StatusCode newStatusCode) {
        // 状态流转规则定义
        // 草稿状态可以转为已提交
        if (this.statusCode == StatusCode.DRAFT && newStatusCode == StatusCode.SUBMITTED) {
            return;
        }
        // 已提交状态可以转为核保中
        if (this.statusCode == StatusCode.SUBMITTED && newStatusCode == StatusCode.UNDERWRITING) {
            return;
        }
        // 核保中状态可以转为核保通过、核保拒绝或核保暂缓
        if (this.statusCode == StatusCode.UNDERWRITING && (newStatusCode == StatusCode.UNDERWRITING_APPROVED
                || newStatusCode == StatusCode.UNDERWRITING_REJECTED
                || newStatusCode == StatusCode.UNDERWRITING_SUSPENDED)) {
            return;
        }
        // 核保通过状态可以转为已承保
        if (this.statusCode == StatusCode.UNDERWRITING_APPROVED && newStatusCode == StatusCode.ISSUED) {
            return;
        }
        // 核保暂缓状态可以重新提交为已提交
        if (this.statusCode == StatusCode.UNDERWRITING_SUSPENDED && newStatusCode == StatusCode.SUBMITTED) {
            return;
        }
        // 核保通过状态可以转为已承保
        if (this.statusCode == StatusCode.UNDERWRITING_APPROVED && newStatusCode == StatusCode.ISSUED) {
            return;
        }
        // 任何状态都可以转为作废
        if (newStatusCode == StatusCode.VOIDED) {
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
         * 草稿
         */
        DRAFT("DRAFT", "草稿"),
        /**
         * 已提交
         */
        SUBMITTED("SUBMITTED", "已提交"),
        /**
         * 核保中
         */
        UNDERWRITING("UNDERWRITING", "核保中"),
        /**
         * 核保通过
         */
        UNDERWRITING_APPROVED("UNDERWRITING_APPROVED", "核保通过"),
        /**
         * 核保拒绝
         */
        UNDERWRITING_REJECTED("UNDERWRITING_REJECTED", "核保拒绝"),
        /**
         * 核保暂缓
         */
        UNDERWRITING_SUSPENDED("UNDERWRITING_SUSPENDED", "核保暂缓"),
        /**
         * 已承保
         */
        ISSUED("ISSUED", "已承保"),
        /**
         * 作废
         */
        VOIDED("VOIDED", "作废");

        private final String code;
        private final String name;

        StatusCode(String code, String name) {
            this.code = code;
            this.name = name;
        }
    }
}
