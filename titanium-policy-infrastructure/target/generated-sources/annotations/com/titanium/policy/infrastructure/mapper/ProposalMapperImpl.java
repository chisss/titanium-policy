package com.titanium.policy.infrastructure.mapper;

import com.titanium.policy.aggregate.Proposal;
import com.titanium.policy.infrastructure.entity.ProposalEntity;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-06-29T17:25:47+0800",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.4 (Amazon.com Inc.)"
)
@Component
public class ProposalMapperImpl implements ProposalMapper {

    @Override
    public ProposalEntity toEntity(Proposal proposal) {
        if ( proposal == null ) {
            return null;
        }

        ProposalEntity proposalEntity = new ProposalEntity();

        proposalEntity.setProposalId( proposal.getProposalId() );
        proposalEntity.setProposalNo( proposal.getProposalNo() );
        proposalEntity.setPolicyForm( proposal.getPolicyForm() );
        proposalEntity.setParentProposalId( proposal.getParentProposalId() );
        proposalEntity.setChannel( proposal.getChannel() );
        proposalEntity.setCreateTime( proposal.getCreateTime() );
        proposalEntity.setUpdateTime( proposal.getUpdateTime() );
        proposalEntity.setTenantId( proposal.getTenantId() );

        return proposalEntity;
    }

    @Override
    public Proposal toAggregate(ProposalEntity entity) {
        if ( entity == null ) {
            return null;
        }

        Proposal.ProposalBuilder proposal = Proposal.builder();

        proposal.proposalId( entity.getProposalId() );
        proposal.proposalNo( entity.getProposalNo() );
        proposal.policyForm( entity.getPolicyForm() );
        proposal.parentProposalId( entity.getParentProposalId() );
        proposal.channel( entity.getChannel() );
        proposal.createTime( entity.getCreateTime() );
        proposal.updateTime( entity.getUpdateTime() );
        proposal.tenantId( entity.getTenantId() );

        return proposal.build();
    }
}
