package com.titanium.policy.application.query;

import org.springframework.stereotype.Service;

import com.titanium.policy.fieldcatalog.PolicyFieldCatalog;
import com.titanium.policy.fieldcatalog.PolicyFieldCatalogCriteria;
import com.titanium.policy.fieldcatalog.PolicyFieldCatalogValidationException;

/** Policy 字段目录应用查询服务。 */
@Service
public class PolicyFieldCatalogApplicationService {

    private final PolicyFieldCatalog currentCatalog = PolicyFieldCatalog.standardV1();

    /**
     * 查询当前目录。
     *
     * <p>首版目录为平台标准目录，查询条件仍被完整校验和回显，为后续产品/租户覆盖保留兼容契约。</p>
     */
    public PolicyFieldCatalog getCurrentCatalog(PolicyFieldCatalogCriteria criteria) {
        if (criteria == null) {
            throw new PolicyFieldCatalogValidationException("字段目录查询条件不能为空");
        }
        return currentCatalog;
    }
}
