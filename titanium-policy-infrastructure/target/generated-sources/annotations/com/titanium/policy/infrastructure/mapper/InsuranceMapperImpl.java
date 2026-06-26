package com.titanium.policy.infrastructure.mapper;

import com.titanium.policy.aggregate.Insurance;
import com.titanium.policy.infrastructure.entity.InsuranceEntity;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-06-25T20:30:56+0800",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.4 (Amazon.com Inc.)"
)
@Component
public class InsuranceMapperImpl implements InsuranceMapper {

    @Override
    public InsuranceEntity toEntity(Insurance insurance) {
        if ( insurance == null ) {
            return null;
        }

        InsuranceEntity insuranceEntity = new InsuranceEntity();

        insuranceEntity.setInsuranceId( insurance.getInsuranceId() );
        insuranceEntity.setInsuranceNo( insurance.getInsuranceNo() );
        insuranceEntity.setProposalId( insurance.getProposalId() );
        insuranceEntity.setPolicyForm( insurance.getPolicyForm() );
        insuranceEntity.setParentInsuranceId( insurance.getParentInsuranceId() );
        insuranceEntity.setCreateTime( insurance.getCreateTime() );
        insuranceEntity.setUpdateTime( insurance.getUpdateTime() );
        insuranceEntity.setTenantId( insurance.getTenantId() );

        return insuranceEntity;
    }

    @Override
    public Insurance toAggregate(InsuranceEntity entity) {
        if ( entity == null ) {
            return null;
        }

        Insurance.InsuranceBuilder insurance = Insurance.builder();

        insurance.insuranceId( entity.getInsuranceId() );
        insurance.insuranceNo( entity.getInsuranceNo() );
        insurance.proposalId( entity.getProposalId() );
        insurance.policyForm( entity.getPolicyForm() );
        insurance.parentInsuranceId( entity.getParentInsuranceId() );
        insurance.createTime( entity.getCreateTime() );
        insurance.updateTime( entity.getUpdateTime() );
        insurance.tenantId( entity.getTenantId() );

        return insurance.build();
    }
}
