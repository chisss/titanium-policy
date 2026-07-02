package com.titanium.policy.command;

import java.time.LocalDateTime;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.policy.common.enums.PolicyDataUpdateType;

/**
 * 应用保单批改命令（数据/要素类批改回写）
 * <p>
 * 由 4A 防腐层在数据/要素类保全执行后下发。Policy 聚合落为不可变批单记录并递增版本号，
 * 不改变保单状态（批改守恒：仅 changesStatus()==false 的类型可走此入口）。批单号由 policy 应用层预生成。
 * </p>
 */
public record ApplyPolicyEndorsementCommand(
        @TargetAggregateIdentifier
        String policyId,
        String endorsementNo,
        PolicyDataUpdateType updateType,
        LocalDateTime endorsementEffectiveDate,
        String changeSummary,
        String originalSnapshot,
        String sourceMaintenanceId,
        String operatorId,
        String tenantId
) {}
