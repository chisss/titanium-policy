package com.titanium.policy.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 给付一期年金命令
 * <p>
 * 年金给付期内，由定时任务在给付日到达时触发，给付一期生存年金。给付以被保险人生存为条件，
 * 每期给付使已给付期数递增；给付满约定总期数后计划完成。年金给付<b>不改变保单状态</b>。
 * </p>
 *
 * @param policyId 保单ID
 * @param operatorId 操作人ID（定时任务系统账号）
 * @param tenantId 租户ID
 */
public record PayAnnuityBenefitCommand(
        @TargetAggregateIdentifier
        String policyId,
        String operatorId,
        String tenantId
) {}
