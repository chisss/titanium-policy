package com.titanium.policy.command;

import java.time.LocalDateTime;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.common.enums.AnnuityPayoutFrequency;

/**
 * 启动年金给付命令
 * <p>
 * 年金保险（{@code InsuranceProductType.ANNUITY}）进入给付期时启动给付计划。仅对生效保单、
 * 且险种为年金的保单可启动；启动后保单进入年金给付期，按频率周期性给付生存年金，<b>不终止保单</b>。
 * </p>
 *
 * @param policyId 保单ID
 * @param startDate 给付起始日
 * @param frequency 给付频率
 * @param amountPerInstallment 每期给付金额
 * @param totalInstallments 总给付期数（null 表示终身年金）
 * @param operatorId 操作人ID
 * @param tenantId 租户ID
 */
public record StartAnnuityPayoutCommand(
        @TargetAggregateIdentifier
        String policyId,
        LocalDateTime startDate,
        AnnuityPayoutFrequency frequency,
        Money amountPerInstallment,
        Integer totalInstallments,
        String operatorId,
        String tenantId
) {}
