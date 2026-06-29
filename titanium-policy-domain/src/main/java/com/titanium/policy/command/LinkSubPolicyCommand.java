package com.titanium.policy.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 挂载子保单命令（团单/父子保单结构，向父保单登记一个子保单）
 * <p>
 * 团单主子联动：父保单（团体总单）下挂多个子保单（被保险人单元）。本命令使独立保单升级为父保单
 * 并登记子保单。子保单侧通过 parentPolicyId 在创建时建立反向引用。
 * </p>
 */
public record LinkSubPolicyCommand(
        @TargetAggregateIdentifier
        String parentPolicyId,
        String childPolicyId,
        String groupId,
        String operatorId,
        String tenantId
) {}
