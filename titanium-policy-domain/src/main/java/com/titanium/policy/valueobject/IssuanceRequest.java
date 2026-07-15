package com.titanium.policy.valueobject;

import java.time.LocalDateTime;

import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.SalesChannel;
import com.titanium.metadata.valueobject.Money;

/**
 * 出单请求
 *
 * @param insuranceType 险种三级分类（寿险 TERM_LIFE/WHOLE_LIFE/ENDOWMENT/ANNUITY 等），
 *                      经出单编排透传至 Proposal/Insurance/Policy 聚合，支撑险种化业务规则
 *                      （满期给付限两全、年金给付限年金险等）；上游未指定时可为 null。
 */
public record IssuanceRequest(
        String productId,
        String productCode,
        PolicyForm policyForm,
        String policyHolderId,
        int insuredCount,
        Money totalPremium,
        LocalDateTime insurancePeriodStart,
        LocalDateTime insurancePeriodEnd,
        SalesChannel channel,
        InsuranceProductType insuranceType,
        String tenantId
) {}
