package com.titanium.policy.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.policy.common.enums.FamilyRelation;
import com.titanium.policy.entity.insurance.InsuredPartyList.InsuredInfo;

/**
 * 新增被保险人（团单加保 / 家庭险增员）命令
 * <p>
 * 团单加入新员工、家庭险新增成员时向保单被保险人清单追加一名被保险人。走独立命令（非批改枚举），
 * 保费联动重算由下游计费域按加保事件处理。家庭险场景通过 {@code familyRelation} 标识成员与投保人关系。
 * </p>
 *
 * @param policyId 保单ID
 * @param member 新增被保险人信息
 * @param familyRelation 家庭成员关系（家庭险必填，团单可为 null）
 * @param operatorId 操作人
 * @param tenantId 租户ID
 */
public record AddInsuredMemberCommand(
        @TargetAggregateIdentifier String policyId,
        InsuredInfo member,
        FamilyRelation familyRelation,
        String operatorId,
        String tenantId) {
}
