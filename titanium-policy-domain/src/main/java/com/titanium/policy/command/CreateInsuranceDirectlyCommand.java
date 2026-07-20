package com.titanium.policy.command;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.policy.entity.insurance.InsuredPartyList;

/**
 * 直接创建投保单命令（两步出单，跳过意向单）
 */
public record CreateInsuranceDirectlyCommand(
        @TargetAggregateIdentifier
        String insuranceId,
        String insuranceNo,
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
        String tenantId,
        /** 基本保额，供 billing 域计算真实保费 */
        BigDecimal sumInsured,
        /** 缴费模式 code */
        String paymentMode,
        /** 缴费年数 */
        int premiumPaymentYears
) {}
