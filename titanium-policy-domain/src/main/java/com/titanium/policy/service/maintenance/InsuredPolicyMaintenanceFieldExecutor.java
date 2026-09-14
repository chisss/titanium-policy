package com.titanium.policy.service.maintenance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import org.springframework.stereotype.Component;

import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.exception.PolicyBusinessRuleException;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceAppliedField;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceExecutionState;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceFieldChange;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceFieldExecution;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceObjectId;

/**
 * 将被保险人身份要素（姓名/证件号码）真实写入 Policy 参与方快照的被保险人集合元素。
 * <p>
 * 两个字段同属 {@code INSURED_INFO_CHANGE} 业务类别、同作用在被保险人集合元素上，故合并为一个执行器
 * （范式同 {@link BeneficiaryPolicyMaintenanceFieldExecutor} 的集合对象多字段合并）。
 * </p>
 * <p>
 * 🔴 <b>与受益人执行器的关键差异——本执行器只改既有元素，不新建元素</b>：被保险人是<b>承保标的</b>，
 * 其增删改变承保风险范围，须经核保并走独立入口（{@code AddInsuredMemberCommand} /
 * {@code RemoveInsuredMemberCommand}）；保全字段批改只允许修正既有被保险人的身份要素。
 * 故目标元素按 {@code insuredId} 定位不到时<b>失败关闭</b>，绝不静默新建（受益人执行器为便于
 * 首次录入列表而允许新建，此处刻意不复用该行为）。
 * </p>
 * <p>
 * 🔴 <b>规范化值口径</b>：两项均为 {@code TEXT}，取去空白后的原文，与字段目录发布的数据类型逐字对应。
 * </p>
 */
@Component
public class InsuredPolicyMaintenanceFieldExecutor implements PolicyMaintenanceFieldExecutor {

    public static final String NAME_FIELD = "policy.insured.name";
    public static final String DOCUMENT_NUMBER_FIELD = "policy.insured.documentNumber";

    private final Map<String, BiFunction<InsuredPartyList.InsuredInfo, String,
            InsuredPartyList.InsuredInfo>> updaters = Map.of(
                    NAME_FIELD, this::withName,
                    DOCUMENT_NUMBER_FIELD, this::withDocumentNumber);

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
        // 对象标识非空由 PolicyMaintenanceFieldChange 的紧凑构造器保证；此处构造标识做长度与字符集校验，
        // 使受理侧传入的非读侧形态标识（超长/非法字符）在此失败关闭，而不是退化为「按顺序改第一个」
        PolicyMaintenanceObjectId requestedId = new PolicyMaintenanceObjectId(change.objectId());
        List<InsuredPartyList.InsuredInfo> insuredList = new ArrayList<>(
                parties.insuredList() == null ? List.of() : parties.insuredList());
        int targetIndex = findTargetIndex(policyId, insuredList, requestedId);
        if (targetIndex < 0) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_FIELD_OBJECT_INVALID",
                    "被保险人不在此保单参与方快照内: " + requestedId.value());
        }
        BiFunction<InsuredPartyList.InsuredInfo, String, InsuredPartyList.InsuredInfo> updater =
                updaters.get(change.fieldCode());
        if (updater == null) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_FIELD_NOT_EXECUTABLE", "被保险人字段尚未开放真实执行: " + change.fieldCode());
        }
        validateDataType(change);
        InsuredPartyList.InsuredInfo updated = updater.apply(insuredList.get(targetIndex),
                change.canonicalValue());
        insuredList.set(targetIndex, updated);
        InsuredPartyList updatedParties = new InsuredPartyList(
                parties.listId(), parties.holderInfo(), insuredList, parties.beneficiaryList());
        PolicyMaintenanceAppliedField applied = new PolicyMaintenanceAppliedField(
                change.itemCode(), change.objectId(), change.fieldCode(), change.dataType(),
                canonicalValue(updated, change.fieldCode()));
        return new PolicyMaintenanceFieldExecution(
                state.withInsuredPartyList(updatedParties), applied);
    }

    private int findTargetIndex(
            String policyId,
            List<InsuredPartyList.InsuredInfo> insuredList,
            PolicyMaintenanceObjectId requestedId) {
        for (int index = 0; index < insuredList.size(); index++) {
            PolicyMaintenanceObjectId existingId = PolicyMaintenanceObjectId.insured(
                    policyId, insuredList.get(index), index);
            if (existingId.equals(requestedId)) {
                return index;
            }
        }
        return -1;
    }

    private void validateDataType(PolicyMaintenanceFieldChange change) {
        String expected = switch (change.fieldCode()) {
            case NAME_FIELD, DOCUMENT_NUMBER_FIELD -> "TEXT";
            default -> throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_FIELD_NOT_EXECUTABLE", "未知被保险人字段: " + change.fieldCode());
        };
        if (!expected.equals(change.dataType())) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_FIELD_TYPE_INVALID", "被保险人字段类型必须为 " + expected);
        }
    }

    private InsuredPartyList.InsuredInfo withName(
            InsuredPartyList.InsuredInfo current,
            String value) {
        return copy(current, requireText(value, "被保险人姓名不能为空"), current.certNo());
    }

    private InsuredPartyList.InsuredInfo withDocumentNumber(
            InsuredPartyList.InsuredInfo current,
            String value) {
        return copy(current, current.name(), requireText(value, "被保险人证件号码不能为空"));
    }

    /** 仅替换姓名或证件号，其余被保险人要素（年龄/性别/关系等）原样透传（record 不可变，批改返回新快照）。 */
    private InsuredPartyList.InsuredInfo copy(
            InsuredPartyList.InsuredInfo current,
            String name,
            String certNo) {
        return new InsuredPartyList.InsuredInfo(
                current.customerId(), current.insuredId(), name, current.certType(), certNo, current.age(),
                current.gender(), current.phone(), current.relationToHolder(), current.familyRelation());
    }

    private String canonicalValue(InsuredPartyList.InsuredInfo insured, String fieldCode) {
        return switch (fieldCode) {
            case NAME_FIELD -> insured.name();
            case DOCUMENT_NUMBER_FIELD -> insured.certNo();
            default -> throw new IllegalStateException("未知被保险人字段: " + fieldCode);
        };
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new PolicyBusinessRuleException("POLICY_MAINTENANCE_FIELD_VALUE_INVALID", message);
        }
        return value.trim();
    }
}
