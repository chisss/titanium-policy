package com.titanium.policy.valueobject.proposal;

import java.time.LocalDateTime;

import com.titanium.policy.exception.PolicyStatusTransitionException;

import lombok.Getter;

/**
 * 投保意向单状态值对象
 * <p>
 * 管控意向单生命周期状态流转，包含状态编码、状态变更时间和变更原因
 * </p>
 *
 * @param statusCode 状态编码
 * @param statusTime 状态变更时间
 * @param changeReason 变更原因
 */
public record ProposalStatus(StatusCode statusCode, LocalDateTime statusTime, String changeReason) {

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
    public ProposalStatus transitionStatus(StatusCode newStatusCode, String changeReason) {
        // 状态流转规则校验
        validateTransition(newStatusCode);
        return new ProposalStatus(newStatusCode, LocalDateTime.now(), changeReason);
    }

    /**
     * 状态流转规则校验
     *
     * @param newStatusCode 新状态编码
     */
    private void validateTransition(StatusCode newStatusCode) {
        // 草稿状态可以转为已提交
        if (this.statusCode == StatusCode.DRAFT && newStatusCode == StatusCode.SUBMITTED) {
            return;
        }
        // 已提交状态可以转为已转投保单或作废
        if (this.statusCode == StatusCode.SUBMITTED
                && (newStatusCode == StatusCode.CONVERTED_TO_APPLICATION || newStatusCode == StatusCode.VOIDED)) {
            return;
        }
        // 其他状态流转不允许
        throw new PolicyStatusTransitionException(
                "投保意向单", "", this.statusCode.name(), newStatusCode.name());
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
         * 已转投保单
         */
        CONVERTED_TO_APPLICATION("CONVERTED_TO_APPLICATION", "已转投保单"),
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
