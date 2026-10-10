package com.titanium.policy.web.mapper;

import org.mapstruct.Mapper;

import com.titanium.policy.api.response.fieldcatalog.PolicyFieldCapabilityResponse;
import com.titanium.policy.api.response.fieldcatalog.PolicyFieldCatalogResponse;
import com.titanium.policy.api.response.fieldcatalog.PolicyFieldDescriptorResponse;
import com.titanium.policy.fieldcatalog.PolicyFieldCapability;
import com.titanium.policy.fieldcatalog.PolicyFieldCatalogCriteria;
import com.titanium.policy.fieldcatalog.PolicyFieldCatalogSnapshot;
import com.titanium.policy.fieldcatalog.PolicyFieldDescriptor;

/** Policy 字段目录的 Web 边界映射器。 */
@Mapper(componentModel = "spring")
public interface PolicyFieldCatalogWebMapper {

    /**
     * 业务时点快照 → 跨域响应。
     *
     * <p>降级标记 {@code fallback} 原样透出（不吞、不反转）——它是消费方区分
     * 「命中该时点版本」与「早于最早版本的降级」的唯一信号，映射层若丢掉它，
     * 契约层的字段就成了摆设。</p>
     */
    default PolicyFieldCatalogResponse toResponse(
            PolicyFieldCatalogCriteria criteria, PolicyFieldCatalogSnapshot snapshot) {
        return new PolicyFieldCatalogResponse(
                criteria.tenantId(),
                criteria.productType(),
                criteria.policyType(),
                criteria.businessDate(),
                snapshot.fallback(),
                snapshot.catalogVersion(),
                snapshot.contentHash(),
                snapshot.catalog().fields().stream().map(this::toResponse).toList());
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
                descriptor.deprecatedAt(),
                // 分析位是 metadata 的跨域共享 record，两界（domain 与 api）本就是同一个类型，
                // 无需也不该再建一层镜像 —— 镜像会在加位时引入「一处加、一处忘」的漂移面。
                descriptor.analysis());
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
