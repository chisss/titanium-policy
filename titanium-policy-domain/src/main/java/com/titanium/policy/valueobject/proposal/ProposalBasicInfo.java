package com.titanium.policy.valueobject.proposal;

import com.titanium.policy.valueobject.Amount;

import java.time.LocalDateTime;

/**
 * 投保意向单基本信息值对象
 * <p>
 * 封装投保意向单的核心基础信息，包括客户ID、意向保额、意向保费、保障期限和意向险种编码
 * </p>
 */
public record ProposalBasicInfo(
        /**
         * 客户ID
         */
        String customerId,
        /**
         * 意向保额
         */
        Amount intendedSumInsured,
        /**
         * 意向保费
         */
        Amount intendedPremium,
        /**
         * 保障期限起期
         */
        LocalDateTime insurancePeriodStart,
        /**
         * 保障期限止期
         */
        LocalDateTime insurancePeriodEnd,
        /**
         * 意向险种编码
         */
        String expectedProductCode
) {
}