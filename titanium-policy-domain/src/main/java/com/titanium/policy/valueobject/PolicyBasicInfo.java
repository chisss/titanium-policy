package com.titanium.policy.valueobject;

import java.time.LocalDateTime;

/**
 * 保单基本信息值对象
 * <p>
 * 封装保单的核心基础信息，包括投保人ID、被保险人数量、总保费等
 * </p>
 */
public record PolicyBasicInfo(
                              /*
                               * 投保人ID
                               */
                              String policyHolderId,
                              /*
                               * 被保险人数量
                               */
                              int insuredCount,
                              /*
                               * 总保费
                               */
                              Amount totalPremium,
                              /*
                               * 保障期限起期
                               */
                              LocalDateTime insurancePeriodStart,
                              /*
                               * 保障期限止期
                               */
                              LocalDateTime insurancePeriodEnd,
                              /*
                               * 保单版本号，批改后递增
                               */
                              int policyVersion,
                              /*
                               * 销售渠道
                               */
                              String channel) {
    /**
     * 创建初始保单基本信息
     * <p>
     * 创建初始版本的保单基本信息
     * </p>
     *
     * @param policyHolderId 投保人ID
     * @param insuredCount 被保险人数量
     * @param totalPremium 总保费
     * @param insurancePeriodStart 保障期限起期
     * @param insurancePeriodEnd 保障期限止期
     * @param channel 销售渠道
     * @return 初始保单基本信息
     */
    public static PolicyBasicInfo createInitial(String policyHolderId, int insuredCount, Amount totalPremium,
                                                LocalDateTime insurancePeriodStart, LocalDateTime insurancePeriodEnd,
                                                String channel) {
        return new PolicyBasicInfo(policyHolderId, insuredCount, totalPremium, insurancePeriodStart, insurancePeriodEnd,
                1, // 初始版本号为1
                channel);
    }

    /**
     * 创建新版本的保单基本信息
     * <p>
     * 批改后创建新版本的保单基本信息，版本号递增
     * </p>
     *
     * @return 新版本的保单基本信息
     */
    public PolicyBasicInfo createNewVersion() {
        return new PolicyBasicInfo(policyHolderId, insuredCount, totalPremium, insurancePeriodStart, insurancePeriodEnd,
                policyVersion + 1, // 版本号递增
                channel);
    }

}
