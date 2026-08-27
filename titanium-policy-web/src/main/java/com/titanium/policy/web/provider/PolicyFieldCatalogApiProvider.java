package com.titanium.policy.web.provider;

import java.time.LocalDate;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.PolicyFieldCatalogApi;
import com.titanium.policy.api.response.fieldcatalog.PolicyFieldCatalogResponse;
import com.titanium.policy.application.query.PolicyFieldCatalogApplicationService;
import com.titanium.policy.fieldcatalog.PolicyFieldCatalog;
import com.titanium.policy.fieldcatalog.PolicyFieldCatalogCriteria;
import com.titanium.policy.web.mapper.PolicyFieldCatalogWebMapper;

import lombok.RequiredArgsConstructor;

/** Policy 字段目录跨域契约实现。 */
@Validated
@RestController
@RequestMapping("/api/v1/policy-field-catalogs")
@RequiredArgsConstructor
public class PolicyFieldCatalogApiProvider implements PolicyFieldCatalogApi {

    private final PolicyFieldCatalogApplicationService applicationService;
    private final PolicyFieldCatalogWebMapper mapper;

    @Override
    public ApiResponse<PolicyFieldCatalogResponse> getCurrentCatalog(
            String tenantId, String productType, String policyType, LocalDate businessDate) {
        PolicyFieldCatalogCriteria criteria =
                new PolicyFieldCatalogCriteria(tenantId, productType, policyType, businessDate);
        PolicyFieldCatalog catalog = applicationService.getCurrentCatalog(criteria);
        return ApiResponse.success(mapper.toResponse(criteria, catalog));
    }
}
