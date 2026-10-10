package com.titanium.policy.query.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.titanium.metadata.enums.policy.PolicyEnum;
import com.titanium.policy.query.result.PolicyInsuredQueryResult;
import com.titanium.policy.query.result.PolicyQueryResult;
import com.titanium.policy.query.view.PolicyInsuredView;
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

    @Test
    void mapsInsuredRowIdentityIntoInsuredId() {
        PolicyInsuredView view = new PolicyInsuredView();
        view.setId("INSURED_001");
        view.setInsuredName("张三");
        view.setRelation("SELF");

        PolicyInsuredQueryResult result = mapper.toInsuredResult(view);

        // 读模型行标识即保全集合字段的对象标识：减员端点以该字段定位成员（G12/g12-06）
        assertEquals("INSURED_001", result.getInsuredId());
        assertEquals("张三", result.getInsuredName());
        assertEquals("SELF", result.getRelationToHolder());
    }
}
