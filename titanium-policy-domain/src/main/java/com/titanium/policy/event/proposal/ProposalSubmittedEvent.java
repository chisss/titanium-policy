package com.titanium.policy.event.proposal;

import java.time.LocalDateTime;

/**
 * 意向单提交事件
 *
 * @param proposalId 意向单ID
 * @param changeReason 变更原因
 * @param submitTime 提交时间
 * @param tenantId 租户ID
 */
public record ProposalSubmittedEvent(
        String proposalId,
        String changeReason,
        LocalDateTime submitTime,
        String tenantId
) {}
