package com.titanium.policy.command;

import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.SalesChannel;
import com.titanium.policy.entity.InsuranceProduct;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.valueobject.Amount;

/**
 * 一步出单直接创建保单命令（不经过投保单）
 */
public record CreatePolicyDirectlyCommand(
        @TargetAggregateIdentifier
        String policyId,
        String policyNo,
        String productId,
        String productCode,
        PolicyForm policyForm,
        String policyHolderId,
        int insuredCount,
        Amount totalPremium,
        LocalDateTime insurancePeriodStart,
        LocalDateTime insurancePeriodEnd,
        SalesChannel channel,
        String tenantId
) {}
