package com.titanium.policy.event.insurance;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.policy.entity.insurance.InsuredPartyList;

/**
 * 投保单创建事件
 */
public record InsuranceCreatedEvent(
        String insuranceId,
        String insuranceNo,
        String proposalId,
        PolicyForm policyForm,
        String holderId,
        int insuredCount,
        BigDecimal exactPremium,
        LocalDateTime insurancePeriodStart,
        LocalDateTime insurancePeriodEnd,
        List<String> productCodes,
        int underwritingPriority,
        InsuredPartyList insuredPartyList,
        InsuranceProductType insuranceType,
        LocalDateTime createTime,
        String tenantId,
        /** 基本保额（保险金额），供出单时调 billing 计算真实保费，null 时回退 exactPremium */ BigDecimal sumInsured,
        /** 缴费模式 code（LUMP_SUM/ANNUAL/MONTHLY），null 时 billing 侧按产品配置决定 */ String paymentMode,
        /** 缴费年数（年缴=年数；月缴时=月数/12；趸缴=1），0 表示未知 */ int premiumPaymentYears
) {}
