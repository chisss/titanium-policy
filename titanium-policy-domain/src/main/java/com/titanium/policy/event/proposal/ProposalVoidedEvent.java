package com.titanium.policy.event.proposal;

import java.time.LocalDateTime;

/**
 * 意向单作废事件
 *
 * @param proposalId 意向单ID
 * @param changeReason 作废原因
 * @param voidTime 作废时间
 * @param tenantId 租户ID
 */
public record ProposalVoidedEvent(
        String proposalId,
        String changeReason,
        LocalDateTime voidTime,
        String tenantId
) {}
