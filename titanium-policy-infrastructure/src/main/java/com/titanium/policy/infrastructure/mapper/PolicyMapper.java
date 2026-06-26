package com.titanium.policy.infrastructure.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.titanium.metadata.enums.policy.PolicyEnum;
import com.titanium.policy.aggregate.Policy;
import com.titanium.policy.infrastructure.entity.PolicyEntity;
import com.titanium.policy.valueobject.PolicyNo;
import com.titanium.policy.valueobject.PolicyStatus;

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
    @Mapping(source = "policyId", target = "policyId")
    @Mapping(source = "tenantId", target = "tenantId")
    @Mapping(source = "insuranceId", target = "insuranceId")
    @Mapping(source = "policyForm", target = "policyForm")
    @Mapping(source = "parentPolicyId", target = "parentPolicyId")
    @Mapping(source = "issueOrg", target = "issueOrg")
    @Mapping(source = "status.statusCode", target = "policyStatus")
    @Mapping(source = "createTime", target = "createTime")
    @Mapping(source = "issueTime", target = "issueTime")
    PolicyEntity toEntity(Policy policy);

    /**
     * 将保单数据库实体转换为聚合根
     *
     * @param entity 保单数据库实体
     * @return 保单聚合根
     */
    @Mapping(source = "policyId", target = "policyId")
    @Mapping(source = "tenantId", target = "tenantId")
    @Mapping(source = "insuranceId", target = "insuranceId")
    @Mapping(source = "policyForm", target = "policyForm")
    @Mapping(source = "parentPolicyId", target = "parentPolicyId")
    @Mapping(source = "issueOrg", target = "issueOrg")
    @Mapping(source = "createTime", target = "createTime")
    @Mapping(source = "issueTime", target = "issueTime")
    Policy toAggregate(PolicyEntity entity);

    /**
     * 保单号值对象 → 字符串
     *
     * @param policyNo 保单号值对象
     * @return 保单号字符串
     */
    default String map(PolicyNo policyNo) {
        return policyNo == null ? null : policyNo.value();
    }

    /**
     * 字符串 → 保单号值对象
     *
     * @param value 保单号字符串
     * @return 保单号值对象
     */
    default PolicyNo map(String value) {
        return value == null ? null : new PolicyNo(value);
    }

    /**
     * 本地状态机编码 → 读侧持久化 metadata 保单状态枚举
     * <p>
     * NOT_EFFECTIVE 对齐 metadata 的 PENDING_EFFECTIVE，其余同名映射。
     * </p>
     *
     * @param statusCode 本地状态机编码
     * @return metadata 保单状态枚举
     */
    default PolicyEnum.PolicyStatus map(PolicyStatus.StatusCode statusCode) {
        if (statusCode == null) {
            return null;
        }
        return switch (statusCode) {
            case NOT_EFFECTIVE -> PolicyEnum.PolicyStatus.PENDING_EFFECTIVE;
            case EFFECTIVE -> PolicyEnum.PolicyStatus.EFFECTIVE;
            case SUSPENDED -> PolicyEnum.PolicyStatus.SUSPENDED;
            case TERMINATED -> PolicyEnum.PolicyStatus.TERMINATED;
            case EXPIRED -> PolicyEnum.PolicyStatus.EXPIRED;
            case CANCELLED -> PolicyEnum.PolicyStatus.CANCELLED;
        };
    }
}