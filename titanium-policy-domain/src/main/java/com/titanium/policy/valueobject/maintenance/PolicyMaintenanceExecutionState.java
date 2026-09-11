package com.titanium.policy.valueobject.maintenance;

import java.util.List;

import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.entity.policy.PolicyProduct;
import com.titanium.policy.valueobject.PremiumPlan;

/**
 * 字段执行器可变更的 Policy 合同子状态集合。
 * <p>
 * 执行器遵循「只改自己关心的部分、用 {@code withXxx} 原样透传其余部分」的约定。
 * 这保证新增一类可执行字段时，只需补一个访问器与一个 {@code withXxx}，
 * 既有执行器无需改动（开闭原则）——先前用双参构造器重建状态时，
 * 新加字段会在每个旧执行器的重建点被静默置空。
 * </p>
 * <p>
 * 本状态随 {@code PolicyMaintenanceAppliedEvent} 落事件存储，新增字段须保留窄构造器
 * 兼容存量事件回放（Jackson 反序列化走规范构造器、缺失字段传 null，窄构造器供代码与测试重建）。
 * </p>
 */
public record PolicyMaintenanceExecutionState(
        InsuredPartyList insuredPartyList,
        List<PolicyProduct> policyProducts,
        PremiumPlan premiumPlan) {

    public PolicyMaintenanceExecutionState {
        policyProducts = policyProducts == null ? null : List.copyOf(policyProducts);
    }

    /** 兼容仅包含参与方快照的存量保全事件。 */
    public PolicyMaintenanceExecutionState(InsuredPartyList insuredPartyList) {
        this(insuredPartyList, null, null);
    }

    /** 兼容不含保费计划的存量保全事件（premiumPlan 属后加字段）。 */
    public PolicyMaintenanceExecutionState(InsuredPartyList insuredPartyList, List<PolicyProduct> policyProducts) {
        this(insuredPartyList, policyProducts, null);
    }

    /** 替换参与方快照，其余合同子状态原样保留。 */
    public PolicyMaintenanceExecutionState withInsuredPartyList(InsuredPartyList parties) {
        return new PolicyMaintenanceExecutionState(parties, policyProducts, premiumPlan);
    }

    /** 替换险种段清单，其余合同子状态原样保留。 */
    public PolicyMaintenanceExecutionState withPolicyProducts(List<PolicyProduct> products) {
        return new PolicyMaintenanceExecutionState(insuredPartyList, products, premiumPlan);
    }

    /** 替换保费计划，其余合同子状态原样保留。 */
    public PolicyMaintenanceExecutionState withPremiumPlan(PremiumPlan plan) {
        return new PolicyMaintenanceExecutionState(insuredPartyList, policyProducts, plan);
    }
}
