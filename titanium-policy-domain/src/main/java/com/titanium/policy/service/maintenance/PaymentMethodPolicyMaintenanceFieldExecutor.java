package com.titanium.policy.service.maintenance;

import org.springframework.stereotype.Component;

import com.titanium.metadata.enums.BaseEnum;
import com.titanium.policy.common.enums.PremiumPaymentMethod;
import com.titanium.policy.exception.PolicyBusinessRuleException;
import com.titanium.policy.valueobject.PremiumPlan;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceAppliedField;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceExecutionState;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceFieldChange;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceFieldExecution;

/**
 * 将缴费方式（趸缴/期缴）真实写入 Policy 保费计划。
 * <p>
 * 本执行器只替换 {@link PremiumPlan#paymentMethod()}，保费金额、缴费周期、到期日与缴费状态一律保留——
 * 缴费方式变更属字段级变更，不触发缴费计划重算（重算涉及保费精算，属独立业务决策）。
 * </p>
 */
@Component
public class PaymentMethodPolicyMaintenanceFieldExecutor implements PolicyMaintenanceFieldExecutor {

    public static final String FIELD_CODE = "policy.payment.method";

    @Override
    public String fieldCode() {
        return FIELD_CODE;
    }

    @Override
    public PolicyMaintenanceFieldExecution execute(
            String policyId,
            PolicyMaintenanceExecutionState state,
            PolicyMaintenanceFieldChange change) {
        if (!"ENUM".equals(change.dataType())) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_FIELD_TYPE_INVALID", "缴费方式必须使用 ENUM 类型");
        }
        if (!policyId.equals(change.objectId())) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_FIELD_OBJECT_INVALID", "非集合字段对象标识必须等于保单ID");
        }
        PremiumPlan plan = state.premiumPlan();
        if (plan == null) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_CONTRACT_INCOMPLETE", "Policy 缺少保费计划快照");
        }
        // 缴费方式不可清空（字段目录 clearable=false），空值与未知码一律失败关闭，不静默保留原值
        String methodCode = normalize(change.canonicalValue());
        PremiumPaymentMethod method = BaseEnum.fromCode(PremiumPaymentMethod.class, methodCode);
        if (method == null) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_FIELD_VALUE_INVALID", "缴费方式取值非法: " + methodCode);
        }
        PremiumPlan updated = new PremiumPlan(
                plan.premiumAmount(), method, plan.paymentCycle(), plan.premiumDueDate(), plan.paymentStatus());
        PolicyMaintenanceAppliedField applied = new PolicyMaintenanceAppliedField(
                change.itemCode(), change.objectId(), change.fieldCode(), change.dataType(), method.getCode());
        return new PolicyMaintenanceFieldExecution(state.withPremiumPlan(updated), applied);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
