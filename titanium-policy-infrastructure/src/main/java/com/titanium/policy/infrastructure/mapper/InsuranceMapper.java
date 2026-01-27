package com.titanium.policy.infrastructure.mapper;



import com.titanium.policy.aggregate.Insurance;
import com.titanium.policy.infrastructure.entity.InsuranceEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

/**
 * 投保单映射接口
 * <p>
 * 用于在投保单聚合根和数据库实体之间进行转换
 * </p>
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface InsuranceMapper {
    InsuranceMapper INSTANCE = Mappers.getMapper(InsuranceMapper.class);

    /**
     * 将投保单聚合根转换为数据库实体
     *
     * @param insurance 投保单聚合根
     * @return 投保单数据库实体
     */
//    @Mapping(source = "status.statusCode.name", target = "statusCode")
//    @Mapping(source = "status.statusTime", target = "statusTime")
//    @Mapping(source = "status.changeReason", target = "changeReason")
//    @Mapping(source = "basicInfo.holderId", target = "holderId")
//    @Mapping(source = "basicInfo.insuredCount", target = "insuredCount")
//    @Mapping(source = "basicInfo.exactPremium.value", target = "exactPremium")
//    @Mapping(source = "basicInfo.exactPremium.currency", target = "currency")
//    @Mapping(source = "basicInfo.insurancePeriodStart", target = "insurancePeriodStart")
//    @Mapping(source = "basicInfo.insurancePeriodEnd", target = "insurancePeriodEnd")
//    @Mapping(source = "basicInfo.underwritingPriority", target = "underwritingPriority")
//    @Mapping(source = "underwritingResult.underwritingId", target = "underwritingId")
//    @Mapping(source = "underwritingResult.resultCode.name", target = "underwritingResultCode")
//    @Mapping(source = "underwritingResult.underwritingOpinion", target = "underwritingOpinion")
//    @Mapping(source = "underwritingResult.underwriterId", target = "underwriterId")
//    @Mapping(source = "underwritingResult.underwritingTime", target = "underwritingTime")
//    @Mapping(source = "underwritingResult.condition", target = "underwritingCondition")
    InsuranceEntity toEntity(Insurance insurance);
    
    /**
     * 将投保单数据库实体转换为聚合根
     *
     * @param entity 投保单数据库实体
     * @return 投保单聚合根
     */
    Insurance toAggregate(InsuranceEntity entity);
}