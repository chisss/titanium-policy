package com.titanium.policy.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.policy.common.enums.DividendOption;

/**
 * 红利派发事件（分红险年度红利处理）
 * <p>
 * 分红型保单按保单年度派发红利并按领取方式处置。携带本次红利金额、领取方式、保单年度与累计红利
 * （留存类方式累积到保单账户）。下游据此：现金/抵缴方式通知计费或支付域，留存类方式在读侧累积红利账户。
 * </p>
 *
 * @param policyId 保单ID
 * @param dividendAmount 本次派发红利金额
 * @param option 红利领取方式
 * @param policyYear 保单年度
 * @param accumulatedDividend 累计红利（留存类方式累加，现金/抵缴方式不累加）
 * @param operatorId 操作人
 * @param occurredAt 事件发生时间
 * @param tenantId 租户ID
 */
public record DividendDistributedEvent(String policyId, BigDecimal dividendAmount, DividendOption option,
                                       int policyYear, BigDecimal accumulatedDividend, String operatorId,
                                       LocalDateTime occurredAt, String tenantId) {
}
