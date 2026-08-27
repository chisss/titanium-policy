package com.titanium.policy.event.proposal;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.titanium.metadata.enums.billing.PremiumCollectionMode;
import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.SalesChannel;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.entity.proposal.ProposalLine;
import com.titanium.policy.valueobject.policy.ChannelInfo;

/**
 * 意向单创建事件
 *
 * @param proposalId 意向单ID
 * @param proposalNo 意向单编号
 * @param policyForm 保单形态
 * @param channel 销售渠道
 * @param customerId 客户ID
 * @param intendedSumInsured 预期保额
 * @param intendedPremium 预期保费
 * @param insurancePeriodStart 保障期限起期
 * @param insurancePeriodEnd 保障期限止期
 * @param expectedProductCode 意向险种编码
 * @param insuranceType 险种三级分类（可空，向后兼容存量事件）
 * @param createTime 创建时间
 * @param tenantId 租户ID
 */
public record ProposalCreatedEvent(
        String proposalId,
        String proposalNo,
        PolicyForm policyForm,
        SalesChannel channel,
        String customerId,
        BigDecimal intendedSumInsured,
        BigDecimal intendedPremium,
        LocalDateTime insurancePeriodStart,
        LocalDateTime insurancePeriodEnd,
        String expectedProductCode,
        /** 意向险种段列表（客户在 App 勾选的险种组合；单险种意向为长度 1，可空以兼容存量事件） */
        List<ProposalLine> proposalLines,
        InsuranceProductType insuranceType,
        /** 出单业务流水号（幂等与进度追溯，可空） */
        String bizNo,
        /** 营销包ID（弱引用，可空） */
        String marketPackageId,
        LocalDateTime createTime,
        String tenantId,
        /** 出单参与方快照（三步出单转换时直接复用） */
        InsuredPartyList insuredPartyList,
        /** 收费方式（三步出单转换时透传） */
        PremiumCollectionMode collectionMode,
        /** 渠道快照（三步出单转换时透传） */
        ChannelInfo channelInfo,
        /** 主险缴费模式 code */
        String paymentMode,
        /** 主险缴费年数 */
        int premiumPaymentYears
) {

    /**
     * 兼容历史事件构造与回放：旧事件没有新增的流程字段。
     */
    public ProposalCreatedEvent(String proposalId, String proposalNo, PolicyForm policyForm, SalesChannel channel,
                                String customerId, BigDecimal intendedSumInsured, BigDecimal intendedPremium,
                                LocalDateTime insurancePeriodStart, LocalDateTime insurancePeriodEnd,
                                String expectedProductCode, List<ProposalLine> proposalLines,
                                InsuranceProductType insuranceType, String bizNo, String marketPackageId,
                                LocalDateTime createTime, String tenantId) {
        this(proposalId, proposalNo, policyForm, channel, customerId, intendedSumInsured, intendedPremium,
                insurancePeriodStart, insurancePeriodEnd, expectedProductCode, proposalLines, insuranceType, bizNo,
                marketPackageId, createTime, tenantId, null, null, null, null, 0);
    }
}
