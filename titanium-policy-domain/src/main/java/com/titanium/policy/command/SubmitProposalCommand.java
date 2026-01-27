package com.titanium.policy.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import lombok.Builder;

/**
 * 提交投保意向单命令
 * <p>
 * 用于提交投保意向单，触发状态变更
 * </p>
 *
 * @param proposalId 聚合根唯一标识
 * @param changeReason 变更原因
 * @param tenantId 租户ID
 */
@Builder
public record SubmitProposalCommand(@TargetAggregateIdentifier String proposalId, String changeReason,
                                    String tenantId) {
}
