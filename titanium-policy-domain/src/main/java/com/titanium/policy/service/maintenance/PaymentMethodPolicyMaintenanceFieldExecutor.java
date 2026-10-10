package com.titanium.policy.service.maintenance;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.titanium.metadata.enums.BaseEnum;
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.common.enums.PremiumPaymentMethod;
import com.titanium.policy.common.enums.PremiumPaymentStatus;
import com.titanium.policy.entity.policy.PolicyProduct;
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
        // 缴费方式不可清空（字段目录 clearable=false），空值与未知码一律失败关闭，不静默保留原值
        String methodCode = normalize(change.canonicalValue());
        PremiumPaymentMethod method = BaseEnum.fromCode(PremiumPaymentMethod.class, methodCode);
        if (method == null) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_FIELD_VALUE_INVALID", "缴费方式取值非法: " + methodCode);
        }
        PremiumPlan updated = plan == null
                ? paidUpPlan(state, method)
                : new PremiumPlan(plan.premiumAmount(), method, plan.paymentCycle(),
                        plan.premiumDueDate(), plan.paymentStatus());
        PolicyMaintenanceAppliedField applied = new PolicyMaintenanceAppliedField(
                change.itemCode(), change.objectId(), change.fieldCode(), change.dataType(), method.getCode());
        return new PolicyMaintenanceFieldExecution(state.withPremiumPlan(updated), applied);
    }

    /**
     * 保单无保费计划快照时，仅「趸缴」可据本次变更就地确立缴清计划。
     * <p>
     * 🔴 <b>为何允许物化</b>：出单链路历史上不组装保费计划快照（{@code CreatePolicyCommand.premiumPlan}
     * 两个生产发送点均传 {@code null}，实测库中 93 张保单 {@code payment_method} 全空），而「趸缴」的领域
     * 含义本身就是「一次性缴清、此后不再缴费」——应缴为零、方式趸缴、状态已缴这三项<b>无需从既有计划继承</b>，
     * 由本次变更直接确立；币种取自主险段合同快照，金额零是「不再缴费」的事实而非猜测。
     * 若维持原判空拒绝，减额缴清（「以现金价值趸缴、停缴」）在任何真实保单上都会必然失败——
     * 守卫将落在无人能到达的路径上。
     * </p>
     * <p>
     * 🔴 <b>期缴仍失败关闭</b>：期缴的金额、缴费周期、到期日缺一即无从确定，任何默认值都是编造合同要素。
     * </p>
     */
    private PremiumPlan paidUpPlan(PolicyMaintenanceExecutionState state, PremiumPaymentMethod method) {
        Money currencySource = resolveCurrencySource(state);
        if (method != PremiumPaymentMethod.SINGLE_PAYMENT || currencySource == null) {
            throw new PolicyBusinessRuleException(PolicyErrorCode.POLICY_RULE_VIOLATION,
                    "违反业务规则[POLICY_MAINTENANCE_CONTRACT_INCOMPLETE]: Policy 缺少保费计划快照");
        }
        return new PremiumPlan(Money.zero(currencySource.currency()), method, null, null,
                PremiumPaymentStatus.PAID);
    }

    /** 保费计划币种取自主险段合同快照（保额优先、缺失回落到段保费），与保额执行器同源。 */
    private Money resolveCurrencySource(PolicyMaintenanceExecutionState state) {
        List<PolicyProduct> products = state.policyProducts();
        if (products == null) {
            return null;
        }
        return products.stream()
                .filter(PolicyProduct::isMain)
                .map(product -> product.sumInsured() != null ? product.sumInsured() : product.premium())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
