package com.titanium.policy.web.mapper;

import com.titanium.policy.query.result.InsuranceQueryResult;
import com.titanium.policy.web.response.InsuranceVO;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-07-01T19:44:35+0800",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.4 (Amazon.com Inc.)"
)
@Component
public class InsuranceWebMapperImpl implements InsuranceWebMapper {

    @Override
    public InsuranceVO toVO(InsuranceQueryResult result) {
        if ( result == null ) {
            return null;
        }

        InsuranceVO insuranceVO = new InsuranceVO();

        insuranceVO.setInsuranceId( result.getInsuranceId() );
        insuranceVO.setInsuranceNo( result.getInsuranceNo() );
        insuranceVO.setProposalId( result.getProposalId() );
        insuranceVO.setPolicyForm( result.getPolicyForm() );
        insuranceVO.setHolderId( result.getHolderId() );
        insuranceVO.setInsuredCount( result.getInsuredCount() );
        insuranceVO.setExactPremium( result.getExactPremium() );
        insuranceVO.setCurrency( result.getCurrency() );
        insuranceVO.setInsurancePeriodStart( result.getInsurancePeriodStart() );
        insuranceVO.setInsurancePeriodEnd( result.getInsurancePeriodEnd() );
        insuranceVO.setStatus( result.getStatus() );
        insuranceVO.setUnderwritingResultCode( result.getUnderwritingResultCode() );
        insuranceVO.setUnderwritingId( result.getUnderwritingId() );
        insuranceVO.setIssuedTime( result.getIssuedTime() );

        return insuranceVO;
    }
}
