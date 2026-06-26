package com.titanium.policy.command;

import java.time.LocalDateTime;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.SalesChannel;
import com.titanium.policy.valueobject.Amount;

import lombok.Builder;

/**
 * 创建投保意向单命令
 * <p>
 * 用于创建投保意向单聚合根
 * </p>
 *
 * @param proposalId 聚合根唯一标识
 * @param proposalNo 意向单编号
 * @param policyForm 保单形态：个单/团单/父子
 * @param channel 销售渠道
 * @param customerId 客户ID
 * @param intendedSumInsured 意向保额
 * @param intendedPremium 意向保费
 * @param insurancePeriodStart 保障期限起期
 * @param insurancePeriodEnd 保障期限止期
 * @param expectedProductCode 意向险种编码
 * @param tenantId 租户ID
 */
@Builder
public record CreateProposalCommand(@TargetAggregateIdentifier String proposalId, String proposalNo,
                                    PolicyForm policyForm, SalesChannel channel, String customerId,
                                    Amount intendedSumInsured, Amount intendedPremium,
                                    LocalDateTime insurancePeriodStart, LocalDateTime insurancePeriodEnd,
                                    String expectedProductCode, String tenantId) {
}
