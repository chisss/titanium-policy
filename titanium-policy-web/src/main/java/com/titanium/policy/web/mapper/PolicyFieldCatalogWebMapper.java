package com.titanium.policy.web.mapper;

import org.mapstruct.Mapper;

import com.titanium.policy.api.response.fieldcatalog.PolicyFieldCapabilityResponse;
import com.titanium.policy.api.response.fieldcatalog.PolicyFieldCatalogResponse;
import com.titanium.policy.api.response.fieldcatalog.PolicyFieldDescriptorResponse;
import com.titanium.policy.fieldcatalog.PolicyFieldCapability;
import com.titanium.policy.fieldcatalog.PolicyFieldCatalog;
import com.titanium.policy.fieldcatalog.PolicyFieldCatalogCriteria;
import com.titanium.policy.fieldcatalog.PolicyFieldDescriptor;

/** Policy 字段目录的 Web 边界映射器。 */
@Mapper(componentModel = "spring")
public interface PolicyFieldCatalogWebMapper {

    default PolicyFieldCatalogResponse toResponse(
            PolicyFieldCatalogCriteria criteria, PolicyFieldCatalog catalog) {
        return new PolicyFieldCatalogResponse(
                criteria.tenantId(),
                criteria.productType(),
                criteria.policyType(),
                criteria.businessDate(),
                catalog.catalogVersion(),
                catalog.contentHash(),
                catalog.fields().stream().map(this::toResponse).toList());
    }

    default PolicyFieldDescriptorResponse toResponse(PolicyFieldDescriptor descriptor) {
        return new PolicyFieldDescriptorResponse(
                descriptor.fieldCode(),
                descriptor.objectType(),
                descriptor.valueType(),
                descriptor.labelKey(),
                descriptor.collection(),
                descriptor.objectIdentityField(),
                toResponse(descriptor.capability()),
                descriptor.sensitivity(),
                descriptor.maskingPolicy(),
                descriptor.deprecatedAt());
    }

    default PolicyFieldCapabilityResponse toResponse(PolicyFieldCapability capability) {
        return new PolicyFieldCapabilityResponse(
                capability.readable(),
                capability.proposable(),
                capability.clearable(),
                capability.executionSupported(),
                capability.requiresObjectId(),
                capability.changeTypeCode());
    }
}
