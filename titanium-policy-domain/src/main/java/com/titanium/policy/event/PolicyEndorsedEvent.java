package com.titanium.policy.event;

import java.time.LocalDateTime;

import com.titanium.policy.valueobject.EndorsementCategory;
import com.titanium.policy.valueobject.PolicyDataUpdateType;

/**
 * 保单已批改事件（取代孤儿事件 PolicyDataUpdatedEvent）
 * <p>
 * 保单生效后数据/要素类批改落地事件。承载批单号、批改类型/分类、独立生效日、变更摘要、原值快照、
 * 来源保全案件ID。
 * </p>
 * <p>
 * <b>版本号说明</b>：{@code versionAfter} 仅为审计快照（事件发布时的预估批改后版本），
 * <b>非事件溯源权威值</b>——保单版本的唯一真相由 Policy 聚合 {@code @EventSourcingHandler} 重放时
 * 调用 {@code incrementVersion()} 产生。读模型/批单记录的版本以重放后的 basicInfo.policyVersion() 为准。
 * </p>
 */
public record PolicyEndorsedEvent(
        String policyId,
        String endorsementNo,
        PolicyDataUpdateType updateType,
        EndorsementCategory category,
        int versionAfter,
        LocalDateTime endorsementEffectiveDate,
        String changeSummary,
        String originalSnapshot,
        boolean requiresPremiumRecalc,
        String sourceMaintenanceId,
        LocalDateTime endorsedAt,
        String operatorId,
        String tenantId
) {}
