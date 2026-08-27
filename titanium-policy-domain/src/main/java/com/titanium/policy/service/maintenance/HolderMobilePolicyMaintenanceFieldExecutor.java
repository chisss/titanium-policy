package com.titanium.policy.service.maintenance;

import org.springframework.stereotype.Component;

import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.exception.PolicyBusinessRuleException;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceAppliedField;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceExecutionState;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceFieldChange;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceFieldExecution;

/** 将投保人手机号真实写入 Policy 参与方快照。 */
@Component
public class HolderMobilePolicyMaintenanceFieldExecutor implements PolicyMaintenanceFieldExecutor {

    public static final String FIELD_CODE = "policy.holder.mobile";

    @Override
    public String fieldCode() {
        return FIELD_CODE;
    }

    @Override
    public PolicyMaintenanceFieldExecution execute(
            String policyId,
            PolicyMaintenanceExecutionState state,
            PolicyMaintenanceFieldChange change) {
        if (!"TEXT".equals(change.dataType())) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_FIELD_TYPE_INVALID", "投保人手机号必须使用 TEXT 类型");
        }
        if (!policyId.equals(change.objectId())) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_FIELD_OBJECT_INVALID", "非集合字段对象标识必须等于保单ID");
        }
        InsuredPartyList parties = state.insuredPartyList();
        if (parties == null || parties.holderInfo() == null) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_CONTRACT_INCOMPLETE", "Policy 缺少投保人合同快照");
        }
        InsuredPartyList.HolderInfo holder = parties.holderInfo();
        String phone = normalize(change.canonicalValue());
        InsuredPartyList.HolderInfo updatedHolder = new InsuredPartyList.HolderInfo(
                holder.customerId(), holder.holderId(), holder.name(), holder.certType(), holder.certNo(), phone);
        InsuredPartyList updated = new InsuredPartyList(
                parties.listId(), updatedHolder, parties.insuredList(), parties.beneficiaryList());
        PolicyMaintenanceAppliedField applied = new PolicyMaintenanceAppliedField(
                change.itemCode(), change.objectId(), change.fieldCode(), change.dataType(), phone);
        return new PolicyMaintenanceFieldExecution(
                new PolicyMaintenanceExecutionState(updated, state.policyProducts()), applied);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
