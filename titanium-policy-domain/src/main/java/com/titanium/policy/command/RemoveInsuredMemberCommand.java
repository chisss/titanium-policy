package com.titanium.policy.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 移除被保险人（团单减保 / 家庭险减员）命令
 * <p>
 * 团单员工离职、家庭险成员退出时从保单被保险人清单移除指定被保险人。移除后不得少于 1 名被保险人。
 * 退费由下游计费域按减保事件处理。
 * </p>
 *
 * @param policyId 保单ID
 * @param insuredId 被移除的被保险人ID
 * @param reason 移除原因
 * @param operatorId 操作人
 * @param tenantId 租户ID
 */
public record RemoveInsuredMemberCommand(
        @TargetAggregateIdentifier String policyId,
        String insuredId,
        String reason,
        String operatorId,
        String tenantId) {
}
