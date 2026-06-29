package com.titanium.policy.command;

import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.valueobject.Money;

import lombok.Builder;

/**
 * 将投保意向单转为投保单命令
 * <p>
 * 用于将投保意向单转换为投保单聚合根
 * </p>
 *
 * @param insuranceId 投保单聚合根唯一标识
 * @param insuranceNo 投保单编号
 * @param proposalId 投保意向单ID
 * @param policyForm 保单形态
 * @param applicantId 投保人ID
 * @param insuredCount 被保险人数量
 * @param exactPremium 精确保费
 * @param insurancePeriodStart 保障期限起期
 * @param insurancePeriodEnd 保障期限止期
 * @param productCodes 投保险种编码列表
 * @param underwritingPriority 核保优先级
 * @param changeReason 变更原因
 * @param tenantId 租户ID
 */
@Builder
public record ConvertProposalToInsuranceCommand(@TargetAggregateIdentifier String insuranceId, String insuranceNo,
                                                String proposalId, PolicyForm policyForm, String applicantId,
                                                int insuredCount, Money exactPremium,
                                                LocalDateTime insurancePeriodStart, LocalDateTime insurancePeriodEnd,
                                                List<String> productCodes, int underwritingPriority,
                                                String changeReason, String tenantId) {
}
