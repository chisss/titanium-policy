package com.titanium.policy.event;

import java.time.LocalDateTime;

import com.titanium.policy.common.enums.FamilyRelation;
import com.titanium.policy.entity.insurance.InsuredPartyList.InsuredInfo;

/**
 * 被保险人已新增事件（团单加保 / 家庭险增员）
 *
 * @param policyId 保单ID
 * @param member 新增被保险人信息
 * @param familyRelation 家庭成员关系（家庭险场景，团单为 null）
 * @param addedAt 新增时间
 * @param operatorId 操作人
 * @param tenantId 租户ID
 */
public record InsuredMemberAddedEvent(
        String policyId,
        InsuredInfo member,
        FamilyRelation familyRelation,
        LocalDateTime addedAt,
        String operatorId,
        String tenantId) {
}
