package com.titanium.policy.valueobject.insurance;

import java.time.LocalDateTime;
import java.util.List;

import com.titanium.metadata.valueobject.Money;

/**
 * 投保单基本信息值对象
 * <p>
 * 封装投保单的核心基础信息，包括投保人ID、被保险人数量、精确保费等
 * </p>
 */
public record InsuranceBasicInfo(
                                 /*
                                  * 投保人ID
                                  */
                                 String holderId,
                                 /*
                                  * 被保险人数量
                                  */
                                 int insuredCount,
                                 /*
                                  * 精确保费
                                  */
                                 Money exactPremium,
                                 /*
                                  * 精确保障期限起期
                                  */
                                 LocalDateTime insurancePeriodStart,
                                 /*
                                  * 精确保障期限止期
                                  */
                                 LocalDateTime insurancePeriodEnd,
                                 /*
                                  * 险种编码列表
                                  */
                                 List<String> productCodeList,
                                 /*
                                  * 核保优先级
                                  */
                                 int underwritingPriority) {
    /**
     * 计算精确保费
     * <p>
     * 调用产品域规则计算精确保费
     * </p>
     *
     * @return 精确保费
     */
    public Money calculateExactPremium() {
        // 这里应该调用产品域的保费计算规则
        // 暂时返回当前精确保费
        return this.exactPremium;
    }
}
