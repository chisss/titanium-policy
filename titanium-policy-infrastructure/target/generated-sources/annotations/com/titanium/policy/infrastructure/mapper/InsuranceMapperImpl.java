package com.titanium.policy.infrastructure.mapper;

import com.titanium.policy.aggregate.Insurance;
import com.titanium.policy.infrastructure.entity.InsuranceEntity;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-07-02T09:28:37+0800",
    comments = "version: 1.5.5.Final, compiler: Eclipse JDT (IDE) 3.46.100.v20260624-0231, environment: Java 21.0.11 (Eclipse Adoptium)"
)
@Component
public class InsuranceMapperImpl implements InsuranceMapper {

    @Override
    public InsuranceEntity toEntity(Insurance insurance) {
        if ( insurance == null ) {
            return null;
        }

        InsuranceEntity insuranceEntity = new InsuranceEntity();

        insuranceEntity.setTenantId( insurance.getTenantId() );
        insuranceEntity.setCreateTime( insurance.getCreateTime() );
        insuranceEntity.setUpdateTime( insurance.getUpdateTime() );
        insuranceEntity.setInsuranceId( insurance.getInsuranceId() );
        insuranceEntity.setInsuranceNo( insurance.getInsuranceNo() );
        insuranceEntity.setParentInsuranceId( insurance.getParentInsuranceId() );
        insuranceEntity.setPolicyForm( insurance.getPolicyForm() );
        insuranceEntity.setProposalId( insurance.getProposalId() );

        return insuranceEntity;
    }

    @Override
    public Insurance toAggregate(InsuranceEntity entity) {
        if ( entity == null ) {
            return null;
        }

        Insurance.InsuranceBuilder<?, ?> insurance = Insurance.builder();

        insurance.tenantId( entity.getTenantId() );
        insurance.createTime( entity.getCreateTime() );
        insurance.updateTime( entity.getUpdateTime() );
        insurance.insuranceId( entity.getInsuranceId() );
        insurance.insuranceNo( entity.getInsuranceNo() );
        insurance.proposalId( entity.getProposalId() );
        insurance.policyForm( entity.getPolicyForm() );
        insurance.parentInsuranceId( entity.getParentInsuranceId() );

        return insurance.build();
    }
}
