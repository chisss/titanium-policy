package com.titanium.policy.fieldcatalog;

import com.titanium.metadata.exception.DomainException;

/** Policy 字段目录定义或查询条件非法。 */
public class PolicyFieldCatalogValidationException extends DomainException {

    public PolicyFieldCatalogValidationException(String message) {
        super("POLICY_FIELD_CATALOG_INVALID", message);
    }
}
