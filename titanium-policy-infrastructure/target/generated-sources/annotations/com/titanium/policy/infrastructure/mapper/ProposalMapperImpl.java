package com.titanium.policy.infrastructure.mapper;

import com.titanium.policy.aggregate.Proposal;
import com.titanium.policy.infrastructure.entity.ProposalEntity;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-02-11T17:54:11+0800",
    comments = "version: 1.5.5.Final, compiler: Eclipse JDT (IDE) 3.42.0.v20250514-1000, environment: Java 21.0.7 (Eclipse Adoptium)"
)
@Component
public class ProposalMapperImpl implements ProposalMapper {

    @Override
    public ProposalEntity toEntity(Proposal proposal) {
        if ( proposal == null ) {
            return null;
        }

        ProposalEntity proposalEntity = new ProposalEntity();

        proposalEntity.setChannel( proposal.getChannel() );
        proposalEntity.setCreateTime( proposal.getCreateTime() );
        proposalEntity.setParentProposalId( proposal.getParentProposalId() );
        proposalEntity.setPolicyForm( proposal.getPolicyForm() );
        proposalEntity.setProposalId( proposal.getProposalId() );
        proposalEntity.setProposalNo( proposal.getProposalNo() );
        proposalEntity.setTenantId( proposal.getTenantId() );
        proposalEntity.setUpdateTime( proposal.getUpdateTime() );

        return proposalEntity;
    }

    @Override
    public Proposal toAggregate(ProposalEntity entity) {
        if ( entity == null ) {
            return null;
        }

        Proposal.ProposalBuilder proposal = Proposal.builder();

        proposal.channel( entity.getChannel() );
        proposal.createTime( entity.getCreateTime() );
        proposal.parentProposalId( entity.getParentProposalId() );
        proposal.policyForm( entity.getPolicyForm() );
        proposal.proposalId( entity.getProposalId() );
        proposal.proposalNo( entity.getProposalNo() );
        proposal.tenantId( entity.getTenantId() );
        proposal.updateTime( entity.getUpdateTime() );

        return proposal.build();
    }
}
