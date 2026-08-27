package com.titanium.policy.service.maintenance;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.titanium.policy.exception.PolicyBusinessRuleException;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceAppliedField;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceExecutionState;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceFieldChange;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceFieldExecution;

/** 按字段目录能力选择执行器，并对未开放字段失败关闭。 */
@Service
public class PolicyMaintenanceFieldExecutorRegistry {

    private final Map<String, PolicyMaintenanceFieldExecutor> executors;

    public PolicyMaintenanceFieldExecutorRegistry(List<PolicyMaintenanceFieldExecutor> executors) {
        Map<String, PolicyMaintenanceFieldExecutor> indexed = new HashMap<>();
        executors.forEach(executor -> indexed.put(executor.fieldCode(), executor));
        this.executors = Map.copyOf(indexed);
    }

    public ExecutionResult execute(
            String policyId,
            PolicyMaintenanceExecutionState initialState,
            List<PolicyMaintenanceFieldChange> changes) {
        if (changes == null || changes.isEmpty()) {
            throw new PolicyBusinessRuleException("POLICY_MAINTENANCE_REQUEST_INVALID", "保全字段变更不能为空");
        }
        Set<String> keys = new HashSet<>();
        PolicyMaintenanceExecutionState state = initialState;
        java.util.ArrayList<PolicyMaintenanceAppliedField> appliedFields = new java.util.ArrayList<>();
        for (PolicyMaintenanceFieldChange change : changes) {
            if (!keys.add(change.key())) {
                throw new PolicyBusinessRuleException(
                        "POLICY_MAINTENANCE_REQUEST_INVALID", "同一合同字段不能重复提交: " + change.key());
            }
            PolicyMaintenanceFieldExecutor executor = executors.get(change.fieldCode());
            if (executor == null) {
                throw new PolicyBusinessRuleException(
                        "POLICY_MAINTENANCE_FIELD_NOT_EXECUTABLE", "字段尚未开放真实执行: " + change.fieldCode());
            }
            PolicyMaintenanceFieldExecution execution = executor.execute(policyId, state, change);
            state = execution.state();
            appliedFields.add(execution.appliedField());
        }
        return new ExecutionResult(state, List.copyOf(appliedFields));
    }

    public record ExecutionResult(
            PolicyMaintenanceExecutionState state,
            List<PolicyMaintenanceAppliedField> appliedFields) {
    }
}
