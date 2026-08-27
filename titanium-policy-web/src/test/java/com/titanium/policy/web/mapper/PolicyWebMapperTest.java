package com.titanium.policy.web.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.titanium.policy.api.response.PolicyResponse;
import com.titanium.policy.query.result.PolicyQueryResult;
import com.titanium.policy.web.response.PolicyDetailVO;

class PolicyWebMapperTest {

    private final PolicyWebMapper mapper = Mappers.getMapper(PolicyWebMapper.class);

    @Test
    void mapsPolicyDetailFieldsAndUsesProductIdForApiResponse() {
        PolicyQueryResult result = new PolicyQueryResult();
        LocalDateTime waitingPeriodEndDate = LocalDateTime.of(2026, 8, 20, 0, 0);
        LocalDateTime hesitationPeriodEndDate = LocalDateTime.of(2026, 8, 27, 0, 0);
        result.setProposalId("proposal-1");
        result.setUnderwritingId("underwriting-1");
        result.setMarketPackageId("package-1");
        result.setProductId("product-id-1");
        result.setProductCode("product-code-1");
        result.setTotalPremium(new BigDecimal("1200.00"));
        result.setLineCount(2);
        result.setWaitingPeriodEndDate(waitingPeriodEndDate);
        result.setHesitationPeriodEndDate(hesitationPeriodEndDate);
        result.setCollectionMode("ONLINE");
        result.setCollectionStatus("COLLECTED");
        result.setCollectedAmount(new BigDecimal("1200.00"));
        result.setChannelId("channel-1");
        result.setSalesChannel("AGENT");
        result.setAgentId("agent-1");

        PolicyDetailVO detail = mapper.toVO(result);
        PolicyResponse response = mapper.toResponse(result);

        assertEquals("proposal-1", detail.getProposalId());
        assertEquals("underwriting-1", detail.getUnderwritingId());
        assertEquals("package-1", detail.getMarketPackageId());
        assertEquals("product-id-1", detail.getProductId());
        assertEquals(new BigDecimal("1200.00"), detail.getTotalPremium());
        assertEquals(2, detail.getLineCount());
        assertEquals(waitingPeriodEndDate, detail.getWaitingPeriodEndDate());
        assertEquals(hesitationPeriodEndDate, detail.getHesitationPeriodEndDate());
        assertEquals("ONLINE", detail.getCollectionMode());
        assertEquals("COLLECTED", detail.getCollectionStatus());
        assertEquals(new BigDecimal("1200.00"), detail.getCollectedAmount());
        assertEquals("channel-1", detail.getChannelId());
        assertEquals("AGENT", detail.getSalesChannel());
        assertEquals("agent-1", detail.getAgentId());
        assertEquals("product-id-1", response.getProductId());
    }
}
