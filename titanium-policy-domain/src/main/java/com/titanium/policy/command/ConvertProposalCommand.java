package com.titanium.policy.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import lombok.Builder;

/**
 * 意向单转投保单命令
 * <p>
 * 已提交（SUBMITTED）意向单经核保/投保确认后转为正式投保单，触发状态流转为
 * {@code CONVERTED_TO_APPLICATION} 并发布 {@code ProposalConvertedEvent}，供读模型投影与三步出单
 * 后续接力。仅已提交意向单可转换。
 * </p>
 *
 * @param proposalId 聚合根唯一标识
 * @param changeReason 转换原因
 * @param tenantId 租户ID
 */
@Builder
public record ConvertProposalCommand(@TargetAggregateIdentifier String proposalId, String changeReason,
                                     String tenantId) {
}
