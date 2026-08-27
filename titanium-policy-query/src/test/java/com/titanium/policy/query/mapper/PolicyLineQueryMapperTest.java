package com.titanium.policy.query.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.titanium.metadata.enums.policy.PolicyEnum;
import com.titanium.policy.query.result.PolicyQueryResult;
import com.titanium.policy.query.view.PolicyView;

class PolicyLineQueryMapperTest {

    private final PolicyLineQueryMapper mapper = Mappers.getMapper(PolicyLineQueryMapper.class);

    @Test
    void mapsPolicyFieldsWithDifferentNames() {
        LocalDateTime effectiveDate = LocalDateTime.of(2026, 8, 14, 0, 0);
        LocalDateTime expiryDate = LocalDateTime.of(2027, 8, 13, 23, 59);
        PolicyView view = new PolicyView();
        view.setInsuranceId("application-1");
        view.setPolicyStatus(PolicyEnum.PolicyStatus.EFFECTIVE);
        view.setStartDate(effectiveDate);
        view.setEndDate(expiryDate);

        PolicyQueryResult result = mapper.toPolicyResult(view);

        assertEquals("application-1", result.getApplicationId());
        assertEquals(PolicyEnum.PolicyStatus.EFFECTIVE, result.getStatus());
        assertEquals(effectiveDate, result.getEffectiveDate());
        assertEquals(expiryDate, result.getExpiryDate());
    }
}
