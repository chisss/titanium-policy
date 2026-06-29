package com.titanium.policy.valueobject.proposal;

import java.time.LocalDateTime;

import com.titanium.metadata.valueobject.Money;

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
        Money intendedSumInsured,
        /**
         * 意向保费
         */
        Money intendedPremium,
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
