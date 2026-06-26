package com.titanium.policy.valueobject.insurance;

import java.time.LocalDateTime;

import com.titanium.policy.exception.PolicyStatusTransitionException;

import lombok.Getter;

/**
 * 投保单状态值对象
 * <p>
 * 管控投保单生命周期状态流转。
 * 状态机：
 * <pre>
 * DRAFT ──submit()──► SUBMITTED ──submitUnderwriting()──► UNDERWRITING
 *                                                            │
 *                    ┌──────UNDERWRITING_APPROVED ◄──────────┤
 *                    │                                       ├──► UNDERWRITING_REJECTED (终态)
 *                    │      UNDERWRITING_SUSPENDED ◄─────────┘
 *                    │           │
 *                    │           └──resubmit()──► UNDERWRITING
 *                    └──triggerIssuance()──► ISSUED (终态)
 * DRAFT/SUBMITTED ──void()──► VOIDED (终态)
 * </pre>
 * </p>
 */
public record InsuranceStatus(StatusCode statusCode, LocalDateTime statusTime, String changeReason) {

    /**
     * 状态流转方法
     */
    public InsuranceStatus transitionStatus(StatusCode newStatusCode, String changeReason) {
        validateTransition(newStatusCode);
        return new InsuranceStatus(newStatusCode, LocalDateTime.now(), changeReason);
    }

    private void validateTransition(StatusCode newStatusCode) {
        // DRAFT → UNDERWRITING（直接提交核保，跳过 SUBMITTED 中间态）
        if (this.statusCode == StatusCode.DRAFT && newStatusCode == StatusCode.UNDERWRITING) {
            return;
        }
        // DRAFT → SUBMITTED
        if (this.statusCode == StatusCode.DRAFT && newStatusCode == StatusCode.SUBMITTED) {
            return;
        }
        // SUBMITTED → UNDERWRITING
        if (this.statusCode == StatusCode.SUBMITTED && newStatusCode == StatusCode.UNDERWRITING) {
            return;
        }
        // UNDERWRITING → UNDERWRITING_APPROVED/REJECTED/SUSPENDED
        if (this.statusCode == StatusCode.UNDERWRITING && (newStatusCode == StatusCode.UNDERWRITING_APPROVED
                || newStatusCode == StatusCode.UNDERWRITING_REJECTED
                || newStatusCode == StatusCode.UNDERWRITING_SUSPENDED)) {
            return;
        }
        // UNDERWRITING_APPROVED → ISSUED
        if (this.statusCode == StatusCode.UNDERWRITING_APPROVED && newStatusCode == StatusCode.ISSUED) {
            return;
        }
        // UNDERWRITING_SUSPENDED → UNDERWRITING（重新提交核保）
        if (this.statusCode == StatusCode.UNDERWRITING_SUSPENDED && newStatusCode == StatusCode.UNDERWRITING) {
            return;
        }
        // 任何非终态都可以转为 VOIDED
        if (newStatusCode == StatusCode.VOIDED
                && this.statusCode != StatusCode.ISSUED
                && this.statusCode != StatusCode.UNDERWRITING_REJECTED
                && this.statusCode != StatusCode.VOIDED) {
            return;
        }
        throw new PolicyStatusTransitionException(
                "投保单", "", this.statusCode.name(), newStatusCode.name());
    }

    @Getter
    public enum StatusCode {
        /** 草稿 */
        DRAFT("DRAFT", "草稿"),
        /** 已提交 */
        SUBMITTED("SUBMITTED", "已提交"),
        /** 核保中 */
        UNDERWRITING("UNDERWRITING", "核保中"),
        /** 核保通过 */
        UNDERWRITING_APPROVED("UNDERWRITING_APPROVED", "核保通过"),
        /** 核保拒绝（终态） */
        UNDERWRITING_REJECTED("UNDERWRITING_REJECTED", "核保拒绝"),
        /** 核保暂缓 */
        UNDERWRITING_SUSPENDED("UNDERWRITING_SUSPENDED", "核保暂缓"),
        /** 已承保（终态） */
        ISSUED("ISSUED", "已承保"),
        /** 作废（终态） */
        VOIDED("VOIDED", "作废");

        private final String code;
        private final String name;

        StatusCode(String code, String name) {
            this.code = code;
            this.name = name;
        }
    }
}
