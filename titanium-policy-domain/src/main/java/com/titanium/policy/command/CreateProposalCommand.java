package com.titanium.policy.command;

import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.metadata.enums.billing.PremiumCollectionMode;
import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.SalesChannel;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.entity.proposal.ProposalLine;
import com.titanium.policy.entity.proposal.ProposalSubject;
import com.titanium.policy.valueobject.policy.ChannelInfo;

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
 * @param insuranceType 险种三级分类（可空，向后兼容存量事件）
 * @param tenantId 租户ID
 */
@Builder
public record CreateProposalCommand(@TargetAggregateIdentifier String proposalId, String proposalNo,
                                    PolicyForm policyForm, SalesChannel channel, String customerId,
                                    Money intendedSumInsured, Money intendedPremium,
                                    LocalDateTime insurancePeriodStart, LocalDateTime insurancePeriodEnd,
                                    String expectedProductCode, List<ProposalLine> proposalLines,
                                    List<ProposalSubject> proposalSubjects,
                                    InsuranceProductType insuranceType, String bizNo, String marketPackageId,
                                    String tenantId, InsuredPartyList insuredPartyList,
                                    PremiumCollectionMode collectionMode, ChannelInfo channelInfo,
                                    String paymentMode, int premiumPaymentYears) {

    /**
     * 兼容旧调用方：新增的参与方/收费字段均按空值处理。
     */
    public CreateProposalCommand(String proposalId, String proposalNo, PolicyForm policyForm, SalesChannel channel,
                                 String customerId, Money intendedSumInsured, Money intendedPremium,
                                 LocalDateTime insurancePeriodStart, LocalDateTime insurancePeriodEnd,
                                 String expectedProductCode, List<ProposalLine> proposalLines,
                                 InsuranceProductType insuranceType, String bizNo, String marketPackageId,
                                 String tenantId) {
        this(proposalId, proposalNo, policyForm, channel, customerId, intendedSumInsured, intendedPremium,
                insurancePeriodStart, insurancePeriodEnd, expectedProductCode, proposalLines, null,
                insuranceType, bizNo, marketPackageId, tenantId, null, null, null, null, 0);
    }
}
