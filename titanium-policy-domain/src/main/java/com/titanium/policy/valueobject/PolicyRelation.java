package com.titanium.policy.valueobject;

import com.titanium.metadata.enums.policy.PolicyEnum;

/**
 * 保单关系值对象
 * <p>
 * 维护父子保单、集团团单的层级关系
 * </p>
 *
 * @param policyLevel 保单层级：独立/父保单/子保单
 * @param parentPolicyId 父保单ID
 * @param subPolicyCount 子保单数量
 * @param groupId 归属集团ID，团单专属
 */
public record PolicyRelation(PolicyEnum.PolicyLevel policyLevel, String parentPolicyId, int subPolicyCount,
                             String groupId) {

    /**
     * 父保单状态变更时，同步子保单状态
     * <p>
     * 父保单状态变更时，更新子保单的状态
     * </p>
     *
     * @param newStatus 新状态
     */
    public void syncParentStatus(PolicyStatus.StatusCode newStatus) {
        // 这里应该触发子保单状态更新
        // 暂时省略具体实现
    }

}
