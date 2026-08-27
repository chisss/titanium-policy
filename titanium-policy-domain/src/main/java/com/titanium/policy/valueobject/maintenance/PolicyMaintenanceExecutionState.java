package com.titanium.policy.valueobject.maintenance;

import java.util.List;

import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.entity.policy.PolicyProduct;

/** 字段执行器可变更的 Policy 合同子状态集合。 */
public record PolicyMaintenanceExecutionState(
        InsuredPartyList insuredPartyList,
        List<PolicyProduct> policyProducts) {

    public PolicyMaintenanceExecutionState {
        policyProducts = policyProducts == null ? null : List.copyOf(policyProducts);
    }

    /** 兼容仅包含参与方快照的存量保全事件。 */
    public PolicyMaintenanceExecutionState(InsuredPartyList insuredPartyList) {
        this(insuredPartyList, null);
    }
}
