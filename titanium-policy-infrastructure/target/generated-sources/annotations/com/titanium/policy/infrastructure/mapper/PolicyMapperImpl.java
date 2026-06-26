package com.titanium.policy.infrastructure.mapper;

import com.titanium.policy.aggregate.Policy;
import com.titanium.policy.infrastructure.entity.PolicyEntity;
import com.titanium.policy.valueobject.PolicyStatus;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-06-25T20:30:56+0800",
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

        policyEntity.setPolicyId( policy.getPolicyId() );
        policyEntity.setTenantId( policy.getTenantId() );
        policyEntity.setInsuranceId( policy.getInsuranceId() );
        policyEntity.setPolicyForm( policy.getPolicyForm() );
        policyEntity.setParentPolicyId( policy.getParentPolicyId() );
        policyEntity.setIssueOrg( policy.getIssueOrg() );
        policyEntity.setPolicyStatus( map( policyStatusStatusCode( policy ) ) );
        policyEntity.setCreateTime( policy.getCreateTime() );
        policyEntity.setIssueTime( policy.getIssueTime() );
        policyEntity.setPolicyNo( map( policy.getPolicyNo() ) );

        return policyEntity;
    }

    @Override
    public Policy toAggregate(PolicyEntity entity) {
        if ( entity == null ) {
            return null;
        }

        Policy.PolicyBuilder policy = Policy.builder();

        policy.policyId( entity.getPolicyId() );
        policy.tenantId( entity.getTenantId() );
        policy.insuranceId( entity.getInsuranceId() );
        policy.policyForm( entity.getPolicyForm() );
        policy.parentPolicyId( entity.getParentPolicyId() );
        policy.issueOrg( entity.getIssueOrg() );
        policy.createTime( entity.getCreateTime() );
        policy.issueTime( entity.getIssueTime() );
        policy.policyNo( map( entity.getPolicyNo() ) );

        return policy.build();
    }

    private PolicyStatus.StatusCode policyStatusStatusCode(Policy policy) {
        if ( policy == null ) {
            return null;
        }
        PolicyStatus status = policy.getStatus();
        if ( status == null ) {
            return null;
        }
        PolicyStatus.StatusCode statusCode = status.statusCode();
        if ( statusCode == null ) {
            return null;
        }
        return statusCode;
    }
}
