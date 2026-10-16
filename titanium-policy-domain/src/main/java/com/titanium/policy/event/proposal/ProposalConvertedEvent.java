package com.titanium.policy.event.proposal;

import java.time.LocalDateTime;

import org.axonframework.serialization.Revision;

/**
 * 意向单转投保单事件
 *
 * @param proposalId 意向单ID
 * @param changeReason 转换原因
 * @param convertTime 转换时间
 * @param tenantId 租户ID
 */
@Revision("1")
public record ProposalConvertedEvent(
        String proposalId,
        String changeReason,
        LocalDateTime convertTime,
        String tenantId
) {}
