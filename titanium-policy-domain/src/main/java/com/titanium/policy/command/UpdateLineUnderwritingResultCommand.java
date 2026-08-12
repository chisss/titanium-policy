package com.titanium.policy.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;

/**
 * 回写险种段核保结论命令
 * <p>
 * 核保结论<b>按险种段</b>回写，支撑「主险承保通过、某附加险被拒保」——这是一单多险的核心场景，
 * 保单级单一核保结论无法表达。拒保段的保费不计入保单总保费（见
 * {@code PolicyLineStatus.countsTowardTotalPremium()}）。
 * </p>
 *
 * @param policyId        保单ID
 * @param policyProductId 险种段ID
 * @param conclusion      核保结论（通过/条件承保/拒绝/暂缓）
 * @param underwritingId  核保单ID（溯源）
 * @param opinion         核保意见
 * @param operatorId      操作人ID
 * @param tenantId        租户ID
 */
public record UpdateLineUnderwritingResultCommand(@TargetAggregateIdentifier String policyId, String policyProductId,
                                                  ConclusionType conclusion, String underwritingId, String opinion,
                                                  String operatorId, String tenantId) {
}
