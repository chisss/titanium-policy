package com.titanium.policy.infrastructure.mapper;

import com.titanium.policy.aggregate.Policy;
import com.titanium.policy.infrastructure.entity.PolicyEntity;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-01-27T11:21:33+0800",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.4 (Amazon.com Inc.)"
)
@Component
public class PolicyMapperImpl implements PolicyMapper {

    @Override
    public PolicyEntity toEntity(Policy policy) {
        if ( policy == null ) {
            return null;
        }

        PolicyEntity policyEntity = new PolicyEntity();

        return policyEntity;
    }

    @Override
    public Policy toAggregate(PolicyEntity entity) {
        if ( entity == null ) {
            return null;
        }

        Policy.PolicyBuilder policy = Policy.builder();

        return policy.build();
    }
}
