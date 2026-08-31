package com.titanium.policy.valueobject.insurance;

import java.time.LocalDateTime;

import com.titanium.policy.common.enums.InsuranceStatusCode;
import com.titanium.policy.exception.PolicyStatusTransitionException;

/**
 * 投保单状态值对象
 * <p>
 * 管控投保单生命周期状态流转。 状态机：
 *
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
 * 状态编码枚举已迁至 common/enums 的 {@link InsuranceStatusCode}。
 * </p>
 */
public record InsuranceStatus(InsuranceStatusCode statusCode, LocalDateTime statusTime, String changeReason) {

    /**
     * 状态流转方法
     */
    public InsuranceStatus transitionStatus(InsuranceStatusCode newStatusCode, String changeReason) {
        validateTransition(newStatusCode);
        return new InsuranceStatus(newStatusCode, LocalDateTime.now(), changeReason);
    }

    private void validateTransition(InsuranceStatusCode newStatusCode) {
        // DRAFT → UNDERWRITING（直接提交核保，跳过 SUBMITTED 中间态）
        if (this.statusCode == InsuranceStatusCode.DRAFT && newStatusCode == InsuranceStatusCode.UNDERWRITING) {
            return;
        }
        // DRAFT → SUBMITTED
        if (this.statusCode == InsuranceStatusCode.DRAFT && newStatusCode == InsuranceStatusCode.SUBMITTED) {
            return;
        }
        // SUBMITTED → UNDERWRITING
        if (this.statusCode == InsuranceStatusCode.SUBMITTED && newStatusCode == InsuranceStatusCode.UNDERWRITING) {
            return;
        }
        // UNDERWRITING → UNDERWRITING_APPROVED/REJECTED/SUSPENDED
        if (this.statusCode == InsuranceStatusCode.UNDERWRITING
                && (newStatusCode == InsuranceStatusCode.UNDERWRITING_APPROVED
                || newStatusCode == InsuranceStatusCode.UNDERWRITING_REJECTED
                || newStatusCode == InsuranceStatusCode.UNDERWRITING_SUSPENDED)) {
            return;
        }
        // UNDERWRITING_APPROVED → ISSUED
        if (this.statusCode == InsuranceStatusCode.UNDERWRITING_APPROVED && newStatusCode == InsuranceStatusCode.ISSUED) {
            return;
        }
        // UNDERWRITING_SUSPENDED → UNDERWRITING（重新提交核保）
        if (this.statusCode == InsuranceStatusCode.UNDERWRITING_SUSPENDED
                && newStatusCode == InsuranceStatusCode.UNDERWRITING) {
            return;
        }
        // 任何非终态都可以转为 VOIDED
        if (newStatusCode == InsuranceStatusCode.VOIDED && this.statusCode != InsuranceStatusCode.ISSUED
                && this.statusCode != InsuranceStatusCode.UNDERWRITING_REJECTED
                && this.statusCode != InsuranceStatusCode.VOIDED) {
            return;
        }
        throw new PolicyStatusTransitionException("投保单", "", this.statusCode.name(), newStatusCode.name());
    }
}
