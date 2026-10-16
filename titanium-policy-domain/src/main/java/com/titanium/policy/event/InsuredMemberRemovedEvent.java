package com.titanium.policy.event;

import java.time.LocalDateTime;

import org.axonframework.serialization.Revision;

/**
 * 被保险人已移除事件（团单减保 / 家庭险减员）
 *
 * @param policyId 保单ID
 * @param insuredId 被移除的被保险人ID
 * @param reason 移除原因
 * @param removedAt 移除时间
 * @param operatorId 操作人
 * @param tenantId 租户ID
 */
@Revision("1")
public record InsuredMemberRemovedEvent(
        String policyId,
        String insuredId,
        String reason,
        LocalDateTime removedAt,
        String operatorId,
        String tenantId) {
}
