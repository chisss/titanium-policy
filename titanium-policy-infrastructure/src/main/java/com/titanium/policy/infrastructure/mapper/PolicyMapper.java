package com.titanium.policy.infrastructure.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.titanium.policy.aggregate.Policy;
import com.titanium.policy.infrastructure.entity.PolicyEntity;

/**
 * 保单映射接口
 * <p>
 * 用于在保单聚合根和数据库实体之间进行转换
 * </p>
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PolicyMapper {
    /**
     * 将保单聚合根转换为数据库实体
     *
     * @param policy 保单聚合根
     * @return 保单数据库实体
     */
//    @Mapping(source = "policyId", target = "policyId")
//    @Mapping(source = "policyRelation.policyLevel", target = "policyType")
//    @Mapping(source = "policyForm", target = "insuranceType")
//    @Mapping(source = "basicInfo.policyHolderId", target = "policyHolderId")
    PolicyEntity toEntity(Policy policy);
    
    /**
     * 将保单数据库实体转换为聚合根
     *
     * @param entity 保单数据库实体
     * @return 保单聚合根
     */
//    @Mapping(source = "policyId", target = "policyId")
//    @Mapping(source = "policyType", target = "policyRelation.policyLevel")
//    @Mapping(source = "insuranceType", target = "policyForm")
//    @Mapping(source = "policyHolderId", target = "basicInfo.policyHolderId")
    Policy toAggregate(PolicyEntity entity);
}