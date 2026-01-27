package com.titanium.policy.infrastructure.mapper;

import com.titanium.policy.aggregate.Proposal;
import com.titanium.policy.infrastructure.entity.ProposalEntity;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-01-27T11:21:33+0800",
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

        return proposalEntity;
    }

    @Override
    public Proposal toAggregate(ProposalEntity entity) {
        if ( entity == null ) {
            return null;
        }

        Proposal.ProposalBuilder proposal = Proposal.builder();

        return proposal.build();
    }
}
