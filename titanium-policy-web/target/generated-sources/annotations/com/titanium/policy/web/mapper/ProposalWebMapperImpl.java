package com.titanium.policy.web.mapper;

import com.titanium.policy.query.result.ProposalQueryResult;
import com.titanium.policy.web.response.ProposalVO;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-07-01T19:44:35+0800",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.4 (Amazon.com Inc.)"
)
@Component
public class ProposalWebMapperImpl implements ProposalWebMapper {

    @Override
    public ProposalVO toVO(ProposalQueryResult result) {
        if ( result == null ) {
            return null;
        }

        ProposalVO proposalVO = new ProposalVO();

        proposalVO.setProposalId( result.getProposalId() );
        proposalVO.setProposalNo( result.getProposalNo() );
        proposalVO.setPolicyForm( result.getPolicyForm() );
        proposalVO.setChannel( result.getChannel() );
        proposalVO.setCustomerId( result.getCustomerId() );
        proposalVO.setIntendedSumInsured( result.getIntendedSumInsured() );
        proposalVO.setIntendedPremium( result.getIntendedPremium() );
        proposalVO.setInsurancePeriodStart( result.getInsurancePeriodStart() );
        proposalVO.setInsurancePeriodEnd( result.getInsurancePeriodEnd() );
        proposalVO.setExpectedProductCode( result.getExpectedProductCode() );
        proposalVO.setStatus( result.getStatus() );

        return proposalVO;
    }
}
