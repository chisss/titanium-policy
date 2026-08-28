package com.titanium.policy.service.maintenance;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import org.springframework.stereotype.Component;

import com.titanium.metadata.enums.policy.BeneficiaryType;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.exception.PolicyBusinessRuleException;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceAppliedField;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceExecutionState;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceFieldChange;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceFieldExecution;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceObjectId;

/** 将受益人姓名、类型和份额作为同一集合对象的结构化字段真实写入 Policy。 */
@Component
public class BeneficiaryPolicyMaintenanceFieldExecutor implements PolicyMaintenanceFieldExecutor {

    public static final String NAME_FIELD = "policy.beneficiary.name";
    public static final String RELATIONSHIP_FIELD = "policy.beneficiary.relationship";
    public static final String SHARE_FIELD = "policy.beneficiary.share";

    private final Map<String, BiFunction<InsuredPartyList.BeneficiaryInfo, String,
            InsuredPartyList.BeneficiaryInfo>> updaters = Map.of(
                    NAME_FIELD, this::withName,
                    RELATIONSHIP_FIELD, this::withType,
                    SHARE_FIELD, this::withShare);

    @Override
    public String fieldCode() {
        return NAME_FIELD;
    }

    @Override
    public Set<String> fieldCodes() {
        return updaters.keySet();
    }

    @Override
    public PolicyMaintenanceFieldExecution execute(
            String policyId,
            PolicyMaintenanceExecutionState state,
            PolicyMaintenanceFieldChange change) {
        InsuredPartyList parties = state.insuredPartyList();
        if (parties == null) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_CONTRACT_INCOMPLETE", "Policy 缺少参与方合同快照");
        }
        PolicyMaintenanceObjectId requestedId = new PolicyMaintenanceObjectId(change.objectId());
        List<InsuredPartyList.BeneficiaryInfo> beneficiaries = new ArrayList<>(
                parties.beneficiaryList() == null ? List.of() : parties.beneficiaryList());
        int targetIndex = findTargetIndex(policyId, beneficiaries, requestedId);
        InsuredPartyList.BeneficiaryInfo target = targetIndex >= 0
                ? beneficiaries.get(targetIndex)
                : emptyBeneficiary(requestedId.value());
        BiFunction<InsuredPartyList.BeneficiaryInfo, String, InsuredPartyList.BeneficiaryInfo> updater =
                updaters.get(change.fieldCode());
        if (updater == null) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_FIELD_NOT_EXECUTABLE", "受益人字段尚未开放真实执行: " + change.fieldCode());
        }
        validateDataType(change);
        InsuredPartyList.BeneficiaryInfo updated = updater.apply(target, change.canonicalValue());
        if (targetIndex >= 0) {
            beneficiaries.set(targetIndex, updated);
        } else {
            beneficiaries.add(updated);
        }
        InsuredPartyList updatedParties = new InsuredPartyList(
                parties.listId(), parties.holderInfo(), parties.insuredList(), beneficiaries);
        PolicyMaintenanceAppliedField applied = new PolicyMaintenanceAppliedField(
                change.itemCode(), change.objectId(), change.fieldCode(), change.dataType(),
                canonicalValue(updated, change.fieldCode()));
        return new PolicyMaintenanceFieldExecution(
                new PolicyMaintenanceExecutionState(updatedParties, state.policyProducts()), applied);
    }

    private int findTargetIndex(
            String policyId,
            List<InsuredPartyList.BeneficiaryInfo> beneficiaries,
            PolicyMaintenanceObjectId requestedId) {
        for (int index = 0; index < beneficiaries.size(); index++) {
            PolicyMaintenanceObjectId existingId = PolicyMaintenanceObjectId.beneficiary(
                    policyId, beneficiaries.get(index), index);
            if (existingId.equals(requestedId)) {
                return index;
            }
        }
        return -1;
    }

    private InsuredPartyList.BeneficiaryInfo emptyBeneficiary(String beneficiaryId) {
        return new InsuredPartyList.BeneficiaryInfo(
                null, beneficiaryId, null, null, null, null, null, null, 1, 0d);
    }

    private void validateDataType(PolicyMaintenanceFieldChange change) {
        String expected = switch (change.fieldCode()) {
            case NAME_FIELD -> "TEXT";
            case RELATIONSHIP_FIELD -> "ENUM";
            case SHARE_FIELD -> "DECIMAL";
            default -> throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_FIELD_NOT_EXECUTABLE", "未知受益人字段: " + change.fieldCode());
        };
        if (!expected.equals(change.dataType())) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_FIELD_TYPE_INVALID", "受益人字段类型必须为 " + expected);
        }
    }

    private InsuredPartyList.BeneficiaryInfo withName(
            InsuredPartyList.BeneficiaryInfo current,
            String value) {
        String name = requireText(value, "受益人姓名不能为空");
        return copy(current, name, current.beneficiaryType(), current.beneficiaryRatio());
    }

    private InsuredPartyList.BeneficiaryInfo withType(
            InsuredPartyList.BeneficiaryInfo current,
            String value) {
        BeneficiaryType type = BeneficiaryType.fromCode(requireText(value, "受益人类型不能为空"));
        if (type == null) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_FIELD_VALUE_INVALID", "受益人类型必须为 DEATH 或 SURVIVAL");
        }
        return copy(current, current.name(), type, current.beneficiaryRatio());
    }

    private InsuredPartyList.BeneficiaryInfo withShare(
            InsuredPartyList.BeneficiaryInfo current,
            String value) {
        BigDecimal percentage;
        try {
            percentage = new BigDecimal(requireText(value, "受益人份额不能为空"));
        } catch (NumberFormatException exception) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_FIELD_VALUE_INVALID", "受益人份额不是有效数字");
        }
        if (percentage.signum() <= 0 || percentage.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_FIELD_VALUE_INVALID", "受益人份额必须大于 0 且不超过 100");
        }
        return copy(current, current.name(), current.beneficiaryType(), percentage.movePointLeft(2).doubleValue());
    }

    private InsuredPartyList.BeneficiaryInfo copy(
            InsuredPartyList.BeneficiaryInfo current,
            String name,
            BeneficiaryType type,
            double ratio) {
        return new InsuredPartyList.BeneficiaryInfo(
                current.customerId(), current.beneficiaryId(), name, current.certType(), current.certNo(),
                current.gender(), current.phone(), type, current.order(), ratio);
    }

    private String canonicalValue(InsuredPartyList.BeneficiaryInfo beneficiary, String fieldCode) {
        return switch (fieldCode) {
            case NAME_FIELD -> beneficiary.name();
            case RELATIONSHIP_FIELD -> beneficiary.beneficiaryType().getCode();
            case SHARE_FIELD -> BigDecimal.valueOf(beneficiary.beneficiaryRatio())
                    .movePointRight(2).stripTrailingZeros().toPlainString();
            default -> throw new IllegalStateException("未知受益人字段: " + fieldCode);
        };
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new PolicyBusinessRuleException("POLICY_MAINTENANCE_FIELD_VALUE_INVALID", message);
        }
        return value.trim();
    }
}
