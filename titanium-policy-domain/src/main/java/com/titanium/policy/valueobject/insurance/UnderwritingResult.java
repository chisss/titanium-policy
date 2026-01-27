package com.titanium.policy.valueobject.insurance;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 核保结果值对象
 * <p>
 * 存储核保域返回的结果，作为承保依据
 * </p>
 *
 * @param underwritingId 核保单号
 * @param resultCode 核保结论
 * @param underwritingOpinion 核保意见
 * @param underwriterId 核保人ID
 * @param underwritingTime 核保时间
 * @param condition 承保条件，如加费
 */
public record UnderwritingResult(String underwritingId, ResultCode resultCode, String underwritingOpinion,
                                 String underwriterId, LocalDateTime underwritingTime, String condition) {
    /**
     * 核保结论枚举
     */
    @Getter
    public enum ResultCode {
        /**
         * 通过
         */
        APPROVED("APPROVED", "通过"),
        /**
         * 拒绝
         */
        REJECTED("REJECTED", "拒绝"),
        /**
         * 暂缓
         */
        SUSPENDED("SUSPENDED", "暂缓");

        private final String code;
        private final String name;

        ResultCode(String code, String name) {
            this.code = code;
            this.name = name;
        }
    }
}
