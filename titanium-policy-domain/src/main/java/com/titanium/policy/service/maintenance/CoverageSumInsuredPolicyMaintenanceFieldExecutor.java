package com.titanium.policy.service.maintenance;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.entity.policy.PolicyProduct;
import com.titanium.policy.exception.PolicyBusinessRuleException;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceAppliedField;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceExecutionState;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceFieldChange;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceFieldExecution;

/** 将主险段基本保额真实写入 Policy 合同快照。 */
@Component
public class CoverageSumInsuredPolicyMaintenanceFieldExecutor implements PolicyMaintenanceFieldExecutor {

    public static final String FIELD_CODE = "policy.coverage.sumInsured";

    @Override
    public String fieldCode() {
        return FIELD_CODE;
    }

    @Override
    public PolicyMaintenanceFieldExecution execute(
            String policyId,
            PolicyMaintenanceExecutionState state,
            PolicyMaintenanceFieldChange change) {
        if (!"DECIMAL".equals(change.dataType())) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_FIELD_TYPE_INVALID", "基本保额必须使用 DECIMAL 类型");
        }
        List<PolicyProduct> products = state.policyProducts();
        if (products == null || products.isEmpty()) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_CONTRACT_INCOMPLETE", "Policy 缺少险种段合同快照");
        }
        PolicyProduct target = products.stream()
                .filter(PolicyProduct::isMain)
                .filter(product -> change.objectId().equals(product.policyProductId()))
                .findFirst()
                .orElseThrow(() -> new PolicyBusinessRuleException(
                        "POLICY_MAINTENANCE_FIELD_OBJECT_INVALID", "基本保额对象标识必须为主险段ID"));
        Money currencySource = target.sumInsured() != null ? target.sumInsured() : target.premium();
        if (currencySource == null) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_CONTRACT_INCOMPLETE", "主险段缺少保额币种");
        }
        BigDecimal amount = parsePositiveAmount(change.canonicalValue());
        Money updatedAmount = Money.of(amount, currencySource.currency());
        List<PolicyProduct> updatedProducts = new ArrayList<>(products.size());
        for (PolicyProduct product : products) {
            updatedProducts.add(product == target ? product.withSumInsured(updatedAmount) : product);
        }
        String canonicalValue = updatedAmount.value().stripTrailingZeros().toPlainString();
        PolicyMaintenanceAppliedField applied = new PolicyMaintenanceAppliedField(
                change.itemCode(), change.objectId(), change.fieldCode(), change.dataType(), canonicalValue);
        return new PolicyMaintenanceFieldExecution(
                new PolicyMaintenanceExecutionState(state.insuredPartyList(), updatedProducts), applied);
    }

    private BigDecimal parsePositiveAmount(String value) {
        if (value == null || value.isBlank()) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_FIELD_VALUE_INVALID", "基本保额不能为空");
        }
        try {
            BigDecimal amount = new BigDecimal(value.trim());
            if (amount.signum() <= 0) {
                throw new PolicyBusinessRuleException(
                        "POLICY_MAINTENANCE_FIELD_VALUE_INVALID", "基本保额必须大于零");
            }
            return amount;
        } catch (NumberFormatException exception) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_FIELD_VALUE_INVALID", "基本保额不是有效数字");
        }
    }
}
