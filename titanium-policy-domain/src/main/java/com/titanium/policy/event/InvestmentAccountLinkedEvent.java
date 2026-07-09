package com.titanium.policy.event;

import java.time.LocalDateTime;

/**
 * 投资账户已挂接事件（投连/万能保单关联投资账户）
 *
 * @param policyId 保单ID
 * @param investmentAccountId 投资账户ID
 * @param linkedAt 挂接时间
 * @param operatorId 操作人
 * @param tenantId 租户ID
 */
public record InvestmentAccountLinkedEvent(
        String policyId,
        String investmentAccountId,
        LocalDateTime linkedAt,
        String operatorId,
        String tenantId) {
}
