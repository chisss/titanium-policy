package com.titanium.policy.event;

import java.time.LocalDateTime;

import com.titanium.policy.common.enums.PremiumWaiverReason;

/**
 * 保费豁免事件（寿险保费豁免条款）
 * <p>
 * 投保人/被保险人发生约定事件，依保费豁免条款豁免后续应缴保费。保单<b>保持 EFFECTIVE</b>、保障不变，
 * 仅标记进入豁免状态。下游据此停止向计费域生成后续应缴账单、读侧记录豁免标记与原因。
 * </p>
 *
 * @param policyId 保单ID
 * @param reason 豁免原因
 * @param operatorId 操作人
 * @param occurredAt 事件发生时间
 * @param tenantId 租户ID
 */
public record PremiumWaivedEvent(String policyId, PremiumWaiverReason reason, String operatorId,
                                 LocalDateTime occurredAt, String tenantId) {
}
