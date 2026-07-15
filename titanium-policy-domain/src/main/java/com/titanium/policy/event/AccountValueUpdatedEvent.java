package com.titanium.policy.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 投资账户价值已回写事件（投连/万能保单账户价值更新）
 *
 * @param policyId 保单ID
 * @param accountId 投资账户ID
 * @param accountValue 最新账户价值金额
 * @param currency 币种
 * @param updatedAt 回写时间
 * @param tenantId 租户ID
 */
public record AccountValueUpdatedEvent(
        String policyId,
        String accountId,
        BigDecimal accountValue,
        String currency,
        LocalDateTime updatedAt,
        String tenantId) {
}
