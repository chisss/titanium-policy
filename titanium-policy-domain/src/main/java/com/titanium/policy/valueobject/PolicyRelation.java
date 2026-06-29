package com.titanium.policy.valueobject;

import com.titanium.metadata.enums.policy.PolicyEnum;

/**
 * 保单关系值对象（不可变）
 * <p>
 * 维护父子保单/集团团单的层级关系。作为不可变值对象，所有"变更"返回新实例；
 * 跨聚合的父子状态级联不属于本值对象职责（值对象无法变更兄弟聚合），由领域事件驱动的
 * 级联编排器实现（见 ParentPolicyCascadeListener）。
 * </p>
 *
 * @param policyLevel 保单层级：独立/父保单/子保单
 * @param parentPolicyId 父保单ID（子保单专属）
 * @param subPolicyCount 子保单数量（父保单专属）
 * @param groupId 归属集团ID（团单专属）
 */
public record PolicyRelation(PolicyEnum.PolicyLevel policyLevel, String parentPolicyId, int subPolicyCount,
                             String groupId) {

    /**
     * 创建独立保单关系
     *
     * @return 独立层级关系
     */
    public static PolicyRelation independent() {
        return new PolicyRelation(PolicyEnum.PolicyLevel.INDEPENDENT, null, 0, null);
    }

    /**
     * 是否父保单
     *
     * @return true 表示父保单
     */
    public boolean isParent() {
        return this.policyLevel == PolicyEnum.PolicyLevel.PARENT;
    }

    /**
     * 是否子保单
     *
     * @return true 表示子保单
     */
    public boolean isChild() {
        return this.policyLevel == PolicyEnum.PolicyLevel.CHILD;
    }

    /**
     * 是否独立保单
     *
     * @return true 表示独立保单
     */
    public boolean isIndependent() {
        return this.policyLevel == PolicyEnum.PolicyLevel.INDEPENDENT;
    }

    /**
     * 挂载一个子保单：独立保单升级为父保单，父保单子单计数+1
     *
     * @return 挂载子保单后的新关系
     */
    public PolicyRelation linkChild() {
        if (isChild()) {
            throw new IllegalStateException("子保单不可再挂载子保单");
        }
        PolicyEnum.PolicyLevel level = PolicyEnum.PolicyLevel.PARENT;
        return new PolicyRelation(level, this.parentPolicyId, this.subPolicyCount + 1, this.groupId);
    }

    /**
     * 归属到父保单：独立保单成为子保单
     *
     * @param parentPolicyId 父保单ID
     * @param groupId 集团ID
     * @return 子保单层级关系
     */
    public PolicyRelation attachToParent(String parentPolicyId, String groupId) {
        if (!isIndependent()) {
            throw new IllegalStateException("仅独立保单可归属为子保单");
        }
        return new PolicyRelation(PolicyEnum.PolicyLevel.CHILD, parentPolicyId, 0, groupId);
    }

}
