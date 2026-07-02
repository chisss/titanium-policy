package com.titanium.policy.entity;

import java.time.LocalDateTime;

import com.titanium.policy.common.enums.EndorsementCategory;
import com.titanium.policy.common.enums.PolicyDataUpdateType;

/**
 * 批单实体（聚合内不可变记录）
 * <p>
 * 保单生效后每次数据/要素类批改在 Policy 聚合内留下的批单留痕：批单号 + 批改类型 + 分类 +
 * 批改后版本号 + 独立生效日 + 变更摘要 + 原值快照 + 来源保全案件ID。批单是"已生效的结果凭证"，
 * 不承载审批流程（审批由 maintenance 案件承载）。
 * </p>
 *
 * @param endorsementNo 批单号（policy 域生成的业务凭证）
 * @param updateType 批改类型
 * @param category 批改大类
 * @param policyVersion 批改后保单版本号
 * @param endorsementEffectiveDate 批单生效日
 * @param changeSummary 变更摘要
 * @param originalSnapshot 批改前原值快照（文本承载，后续可结构化）
 * @param requiresPremiumRecalc 是否触发保费重算
 * @param sourceMaintenanceId 来源保全案件ID
 * @param endorsedAt 批改落地时间
 * @param operatorId 操作人
 */
public record Endorsement(
        String endorsementNo,
        PolicyDataUpdateType updateType,
        EndorsementCategory category,
        int policyVersion,
        LocalDateTime endorsementEffectiveDate,
        String changeSummary,
        String originalSnapshot,
        boolean requiresPremiumRecalc,
        String sourceMaintenanceId,
        LocalDateTime endorsedAt,
        String operatorId) {
}
