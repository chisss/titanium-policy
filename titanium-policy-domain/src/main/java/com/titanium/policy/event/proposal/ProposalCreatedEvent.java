package com.titanium.policy.event.proposal;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.SalesChannel;

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
        InsuranceProductType insuranceType,
        LocalDateTime createTime,
        String tenantId
) {}
