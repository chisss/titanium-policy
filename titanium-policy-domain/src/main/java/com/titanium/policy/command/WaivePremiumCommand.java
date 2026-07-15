package com.titanium.policy.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.policy.common.enums.PremiumWaiverReason;

/**
 * 保费豁免命令（寿险保费豁免条款）
 * <p>
 * 投保人/被保险人发生约定事件（身故、全残、重疾）时，依保费豁免条款豁免后续应缴保费，保单持续有效、
 * 保障不变。区别于失效/终止：豁免后保单仍 EFFECTIVE，只是投保人无需再缴费。由核保/理赔确认豁免资格后触发。
 * </p>
 *
 * @param policyId 保单ID
 * @param reason 豁免原因（投保人身故/全残/重疾）
 * @param operatorId 操作人
 * @param tenantId 租户ID
 */
public record WaivePremiumCommand(
        @TargetAggregateIdentifier String policyId,
        PremiumWaiverReason reason,
        String operatorId,
        String tenantId
) {
}
