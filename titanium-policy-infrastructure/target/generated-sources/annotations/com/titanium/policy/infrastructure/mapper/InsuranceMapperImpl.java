package com.titanium.policy.infrastructure.mapper;

import com.titanium.policy.aggregate.Insurance;
import com.titanium.policy.infrastructure.entity.InsuranceEntity;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-01-27T11:21:33+0800",
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

        return insuranceEntity;
    }

    @Override
    public Insurance toAggregate(InsuranceEntity entity) {
        if ( entity == null ) {
            return null;
        }

        Insurance.InsuranceBuilder insurance = Insurance.builder();

        return insurance.build();
    }
}
