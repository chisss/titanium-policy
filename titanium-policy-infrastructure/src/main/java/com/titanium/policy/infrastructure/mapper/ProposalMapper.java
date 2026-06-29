package com.titanium.policy.infrastructure.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import com.titanium.policy.aggregate.Proposal;
import com.titanium.policy.infrastructure.entity.ProposalEntity;

/**
 * 投保意向单映射接口
 * <p>
 * 用于在投保意向单聚合根和数据库实体之间进行转换
 * </p>
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProposalMapper {
    /**
     * 将投保意向单聚合根转换为数据库实体
     *
     * @param proposal 投保意向单聚合根
     * @return 投保意向单数据库实体
     */
//    @Mapping(source = "status.statusCode.name", target = "statusCode")
//    @Mapping(source = "status.statusTime", target = "statusTime")
//    @Mapping(source = "status.changeReason", target = "changeReason")
//    @Mapping(source = "basicInfo.customerId", target = "customerId")
//    @Mapping(source = "basicInfo.intendedSumInsured.value", target = "intendedSumInsured")
//    @Mapping(source = "basicInfo.intendedPremium.value", target = "intendedPremium")
//    @Mapping(source = "basicInfo.intendedPremium.currency", target = "currency")
//    @Mapping(source = "basicInfo.insurancePeriodStart", target = "insurancePeriodStart")
//    @Mapping(source = "basicInfo.insurancePeriodEnd", target = "insurancePeriodEnd")
//    @Mapping(source = "basicInfo.expectedProductCode", target = "expectedProductCode")
    ProposalEntity toEntity(Proposal proposal);

    /**
     * 将投保意向单数据库实体转换为聚合根
     *
     * @param entity 投保意向单数据库实体
     * @return 投保意向单聚合根
     */
    Proposal toAggregate(ProposalEntity entity);
}
