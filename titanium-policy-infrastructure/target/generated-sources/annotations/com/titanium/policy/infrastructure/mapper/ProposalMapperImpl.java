package com.titanium.policy.infrastructure.mapper;

import com.titanium.policy.aggregate.Proposal;
import com.titanium.policy.infrastructure.entity.ProposalEntity;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-07-02T09:28:37+0800",
    comments = "version: 1.5.5.Final, compiler: Eclipse JDT (IDE) 3.46.100.v20260624-0231, environment: Java 21.0.11 (Eclipse Adoptium)"
)
@Component
public class ProposalMapperImpl implements ProposalMapper {

    @Override
    public ProposalEntity toEntity(Proposal proposal) {
        if ( proposal == null ) {
            return null;
        }

        ProposalEntity proposalEntity = new ProposalEntity();

        proposalEntity.setTenantId( proposal.getTenantId() );
        proposalEntity.setCreateTime( proposal.getCreateTime() );
        proposalEntity.setUpdateTime( proposal.getUpdateTime() );
        proposalEntity.setChannel( proposal.getChannel() );
        proposalEntity.setParentProposalId( proposal.getParentProposalId() );
        proposalEntity.setPolicyForm( proposal.getPolicyForm() );
        proposalEntity.setProposalId( proposal.getProposalId() );
        proposalEntity.setProposalNo( proposal.getProposalNo() );

        return proposalEntity;
    }

    @Override
    public Proposal toAggregate(ProposalEntity entity) {
        if ( entity == null ) {
            return null;
        }

        Proposal.ProposalBuilder<?, ?> proposal = Proposal.builder();

        proposal.tenantId( entity.getTenantId() );
        proposal.createTime( entity.getCreateTime() );
        proposal.updateTime( entity.getUpdateTime() );
        proposal.proposalId( entity.getProposalId() );
        proposal.proposalNo( entity.getProposalNo() );
        proposal.policyForm( entity.getPolicyForm() );
        proposal.parentProposalId( entity.getParentProposalId() );
        proposal.channel( entity.getChannel() );

        return proposal.build();
    }
}
