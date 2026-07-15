package com.titanium.policy.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 保单满期给付事件（两全险/生存给付型寿险）
 * <p>
 * 被保险人生存至保险期间届满，给付满期生存保险金并使保单转为满期（EXPIRED，终态）。携带满期给付金额
 * 供下游支付域派发满期金给付、读侧记录满期给付明细。区别于普通 {@code PolicyExpiredEvent}（仅止期到达
 * 无给付）：本事件承载满期金给付。
 * </p>
 *
 * @param policyId 保单ID
 * @param maturityBenefit 满期给付金额
 * @param operatorId 操作人
 * @param occurredAt 事件发生时间
 * @param tenantId 租户ID
 */
public record PolicyMaturedEvent(String policyId, BigDecimal maturityBenefit, String operatorId,
                                 LocalDateTime occurredAt, String tenantId) {
}
