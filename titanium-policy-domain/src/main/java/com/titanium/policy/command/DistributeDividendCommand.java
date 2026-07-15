package com.titanium.policy.command;

import java.math.BigDecimal;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.policy.common.enums.DividendOption;

/**
 * 红利派发命令（分红险年度红利处理）
 * <p>
 * 分红型保单（{@code ParticipationType.PARTICIPATING}）在保单周年日按当年经营盈余派发红利。按投保人选择的
 * 领取方式处置：现金给付 / 累积生息 / 抵缴保费 / 购买交清增额。仅生效的分红型保单可派发。由定时任务在
 * 保单周年日触发，红利金额由精算按当年分红方案核定后传入。
 * </p>
 *
 * @param policyId 保单ID
 * @param dividendAmount 本次派发红利金额
 * @param option 红利领取方式
 * @param policyYear 保单年度（第几个保单年度）
 * @param operatorId 操作人
 * @param tenantId 租户ID
 */
public record DistributeDividendCommand(
        @TargetAggregateIdentifier String policyId,
        BigDecimal dividendAmount,
        DividendOption option,
        int policyYear,
        String operatorId,
        String tenantId
) {
}
