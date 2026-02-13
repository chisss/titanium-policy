package com.titanium.policy.infrastructure.mapper;

import com.titanium.policy.aggregate.Insurance;
import com.titanium.policy.infrastructure.entity.InsuranceEntity;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-02-11T17:54:11+0800",
    comments = "version: 1.5.5.Final, compiler: Eclipse JDT (IDE) 3.42.0.v20250514-1000, environment: Java 21.0.7 (Eclipse Adoptium)"
)
@Component
public class InsuranceMapperImpl implements InsuranceMapper {

    @Override
    public InsuranceEntity toEntity(Insurance insurance) {
        if ( insurance == null ) {
            return null;
        }

        InsuranceEntity insuranceEntity = new InsuranceEntity();

        insuranceEntity.setCreateTime( insurance.getCreateTime() );
        insuranceEntity.setInsuranceId( insurance.getInsuranceId() );
        insuranceEntity.setInsuranceNo( insurance.getInsuranceNo() );
        insuranceEntity.setParentInsuranceId( insurance.getParentInsuranceId() );
        insuranceEntity.setPolicyForm( insurance.getPolicyForm() );
        insuranceEntity.setProposalId( insurance.getProposalId() );
        insuranceEntity.setTenantId( insurance.getTenantId() );
        insuranceEntity.setUpdateTime( insurance.getUpdateTime() );

        return insuranceEntity;
    }

    @Override
    public Insurance toAggregate(InsuranceEntity entity) {
        if ( entity == null ) {
            return null;
        }

        Insurance.InsuranceBuilder insurance = Insurance.builder();

        insurance.createTime( entity.getCreateTime() );
        insurance.insuranceId( entity.getInsuranceId() );
        insurance.insuranceNo( entity.getInsuranceNo() );
        insurance.parentInsuranceId( entity.getParentInsuranceId() );
        insurance.policyForm( entity.getPolicyForm() );
        insurance.proposalId( entity.getProposalId() );
        insurance.tenantId( entity.getTenantId() );
        insurance.updateTime( entity.getUpdateTime() );

        return insurance.build();
    }
}
