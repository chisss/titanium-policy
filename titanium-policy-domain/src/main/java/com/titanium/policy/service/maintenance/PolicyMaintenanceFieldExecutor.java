package com.titanium.policy.service.maintenance;

import java.util.Set;

import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceExecutionState;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceFieldChange;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceFieldExecution;

/** 单一稳定字段码的 Policy 合同执行策略。 */
public interface PolicyMaintenanceFieldExecutor {

    String fieldCode();

    /** 一个策略可原子处理同一业务对象的一组紧密相关字段。 */
    default Set<String> fieldCodes() {
        return Set.of(fieldCode());
    }

    PolicyMaintenanceFieldExecution execute(
            String policyId,
            PolicyMaintenanceExecutionState state,
            PolicyMaintenanceFieldChange change);
}
