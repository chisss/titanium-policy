package com.titanium.policy.fieldcatalog;

import java.time.LocalDate;

/** Policy 字段目录的业务时点查询条件。 */
public record PolicyFieldCatalogCriteria(
        String tenantId, String productType, String policyType, LocalDate businessDate) {

    public PolicyFieldCatalogCriteria {
        if (tenantId == null || tenantId.isBlank()) {
            throw new PolicyFieldCatalogValidationException("租户ID不能为空");
        }
        if (businessDate == null) {
            throw new PolicyFieldCatalogValidationException("业务日期不能为空");
        }
        tenantId = tenantId.trim();
        productType = normalize(productType);
        policyType = normalize(policyType);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
