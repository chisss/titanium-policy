package com.titanium.policy.service.maintenance;

import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceExecutionState;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceFieldChange;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceFieldExecution;

/** 单一稳定字段码的 Policy 合同执行策略。 */
public interface PolicyMaintenanceFieldExecutor {

    String fieldCode();

    PolicyMaintenanceFieldExecution execute(
            String policyId,
            PolicyMaintenanceExecutionState state,
            PolicyMaintenanceFieldChange change);
}
