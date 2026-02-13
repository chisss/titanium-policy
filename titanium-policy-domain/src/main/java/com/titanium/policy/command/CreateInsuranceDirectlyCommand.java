package com.titanium.policy.command;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 直接创建投保单命令（两步出单，跳过意向单）
 */
public record CreateInsuranceDirectlyCommand(
        @TargetAggregateIdentifier
        String insuranceId,
        String insuranceNo,
        String policyForm,
        String holderId,
        int insuredCount,
        BigDecimal exactPremium,
        LocalDateTime insurancePeriodStart,
        LocalDateTime insurancePeriodEnd,
        List<String> productCodes,
        int underwritingPriority,
        String tenantId
) {}
