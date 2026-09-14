package com.titanium.policy.service.maintenance;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import org.springframework.stereotype.Component;

import com.titanium.metadata.enums.customer.CustomerEnum.CustomerGender;
import com.titanium.metadata.enums.customer.CustomerEnum.IdCardType;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.exception.PolicyBusinessRuleException;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceAppliedField;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceExecutionState;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceFieldChange;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceFieldExecution;

/**
 * 将投保人身份要素（姓名/性别/出生日期/证件类型/证件号码）真实写入 Policy 参与方快照。
 * <p>
 * 五个字段同属 {@code HOLDER_CHANGE} 业务类别、同作用在保单唯一投保人对象上，故合并为一个执行器
 * （范式同 {@link BeneficiaryPolicyMaintenanceFieldExecutor} 的集合对象多字段合并）；投保人<b>联系方式</b>
 * （{@code policy.holder.mobile}）业务类别为 {@code POLICY_INFO_CHANGE}，仍由
 * {@link HolderMobilePolicyMaintenanceFieldExecutor} 承担，两者字段码互斥、注册表不冲突。
 * </p>
 * <p>
 * 🔴 <b>为何这五个字段必须落到合同快照而非客户主数据</b>：姓名/性别/出生日期/证件是<b>合同要素</b>
 * （性别、出生日期参与年龄与费率推导），按根规约与 {@code InsuredPartyList.HolderInfo} 的注释约定，
 * 客户主数据变更<b>不得静默改写保单</b>，必须走本执行器留痕批改。
 * </p>
 * <p>
 * 🔴 <b>规范化值口径</b>：{@code DATE} 一律 ISO-8601（{@code yyyy-MM-dd}），{@code ENUM} 一律枚举
 * {@code code}（非 {@code enumCode} 数字），与字段目录发布的数据类型逐字对应。
 * </p>
 */
@Component
public class HolderPolicyMaintenanceFieldExecutor implements PolicyMaintenanceFieldExecutor {

    public static final String NAME_FIELD = "policy.holder.name";
    public static final String GENDER_FIELD = "policy.holder.gender";
    public static final String BIRTH_DATE_FIELD = "policy.holder.birthDate";
    public static final String DOCUMENT_TYPE_FIELD = "policy.holder.documentType";
    public static final String DOCUMENT_NUMBER_FIELD = "policy.holder.documentNumber";

    private final Map<String, BiFunction<InsuredPartyList.HolderInfo, String,
            InsuredPartyList.HolderInfo>> updaters = Map.of(
                    NAME_FIELD, this::withName,
                    GENDER_FIELD, this::withGender,
                    BIRTH_DATE_FIELD, this::withBirthDate,
                    DOCUMENT_TYPE_FIELD, this::withDocumentType,
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
        if (parties == null || parties.holderInfo() == null) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_CONTRACT_INCOMPLETE", "Policy 缺少投保人合同快照");
        }
        if (!policyId.equals(change.objectId())) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_FIELD_OBJECT_INVALID", "非集合字段对象标识必须等于保单ID");
        }
        BiFunction<InsuredPartyList.HolderInfo, String, InsuredPartyList.HolderInfo> updater =
                updaters.get(change.fieldCode());
        if (updater == null) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_FIELD_NOT_EXECUTABLE", "投保人字段尚未开放真实执行: " + change.fieldCode());
        }
        validateDataType(change);
        InsuredPartyList.HolderInfo updated = updater.apply(parties.holderInfo(), change.canonicalValue());
        InsuredPartyList updatedParties = new InsuredPartyList(
                parties.listId(), updated, parties.insuredList(), parties.beneficiaryList());
        PolicyMaintenanceAppliedField applied = new PolicyMaintenanceAppliedField(
                change.itemCode(), change.objectId(), change.fieldCode(), change.dataType(),
                canonicalValue(updated, change.fieldCode()));
        return new PolicyMaintenanceFieldExecution(state.withInsuredPartyList(updatedParties), applied);
    }

    private void validateDataType(PolicyMaintenanceFieldChange change) {
        String expected = switch (change.fieldCode()) {
            case NAME_FIELD, DOCUMENT_NUMBER_FIELD -> "TEXT";
            case GENDER_FIELD, DOCUMENT_TYPE_FIELD -> "ENUM";
            case BIRTH_DATE_FIELD -> "DATE";
            default -> throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_FIELD_NOT_EXECUTABLE", "未知投保人字段: " + change.fieldCode());
        };
        if (!expected.equals(change.dataType())) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_FIELD_TYPE_INVALID", "投保人字段类型必须为 " + expected);
        }
    }

    private InsuredPartyList.HolderInfo withName(InsuredPartyList.HolderInfo current, String value) {
        return copy(current, requireText(value, "投保人姓名不能为空"), current.certType(), current.certNo(),
                current.gender(), current.birthDate());
    }

    private InsuredPartyList.HolderInfo withGender(InsuredPartyList.HolderInfo current, String value) {
        CustomerGender gender = CustomerGender.fromCode(requireText(value, "投保人性别不能为空"));
        if (gender == null) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_FIELD_VALUE_INVALID", "投保人性别必须为 MALE/FEMALE/UNKNOWN");
        }
        return copy(current, current.name(), current.certType(), current.certNo(), gender, current.birthDate());
    }

    private InsuredPartyList.HolderInfo withBirthDate(InsuredPartyList.HolderInfo current, String value) {
        LocalDate birthDate;
        try {
            birthDate = LocalDate.parse(requireText(value, "投保人出生日期不能为空"));
        } catch (DateTimeParseException exception) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_FIELD_VALUE_INVALID", "投保人出生日期必须为 ISO 日期(yyyy-MM-dd)");
        }
        if (birthDate.isAfter(LocalDate.now())) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_FIELD_VALUE_INVALID", "投保人出生日期不能晚于当日");
        }
        return copy(current, current.name(), current.certType(), current.certNo(), current.gender(), birthDate);
    }

    private InsuredPartyList.HolderInfo withDocumentType(InsuredPartyList.HolderInfo current, String value) {
        String code = requireText(value, "投保人证件类型不能为空");
        IdCardType certType = IdCardType.fromCode(code);
        if (certType == null) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_FIELD_VALUE_INVALID", "投保人证件类型不在证件类型枚举内: " + code);
        }
        return copy(current, current.name(), certType, current.certNo(), current.gender(), current.birthDate());
    }

    private InsuredPartyList.HolderInfo withDocumentNumber(InsuredPartyList.HolderInfo current, String value) {
        return copy(current, current.name(), current.certType(),
                requireText(value, "投保人证件号码不能为空"), current.gender(), current.birthDate());
    }

    /** 仅替换传入的合同要素，其余投保人字段原样透传（record 不可变，批改返回新快照）。 */
    private InsuredPartyList.HolderInfo copy(
            InsuredPartyList.HolderInfo current,
            String name,
            IdCardType certType,
            String certNo,
            CustomerGender gender,
            LocalDate birthDate) {
        return new InsuredPartyList.HolderInfo(
                current.customerId(), current.holderId(), name, certType, certNo, current.phone(), gender,
                birthDate);
    }

    private String canonicalValue(InsuredPartyList.HolderInfo holder, String fieldCode) {
        return switch (fieldCode) {
            case NAME_FIELD -> holder.name();
            case GENDER_FIELD -> holder.gender().getCode();
            case BIRTH_DATE_FIELD -> holder.birthDate().toString();
            case DOCUMENT_TYPE_FIELD -> holder.certType().getCode();
            case DOCUMENT_NUMBER_FIELD -> holder.certNo();
            default -> throw new IllegalStateException("未知投保人字段: " + fieldCode);
        };
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new PolicyBusinessRuleException("POLICY_MAINTENANCE_FIELD_VALUE_INVALID", message);
        }
        return value.trim();
    }
}
