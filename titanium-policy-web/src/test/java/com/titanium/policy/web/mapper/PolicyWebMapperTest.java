package com.titanium.policy.web.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.titanium.metadata.enums.customer.CustomerEnum.CustomerGender;
import com.titanium.metadata.enums.customer.CustomerEnum.IdCardType;
import com.titanium.policy.api.request.member.AddInsuredMemberRequest;
import com.titanium.policy.api.response.policy.PolicyResponse;
import com.titanium.policy.command.AddInsuredMemberCommand;
import com.titanium.policy.command.RemoveInsuredMemberCommand;
import com.titanium.policy.common.enums.FamilyRelation;
import com.titanium.policy.query.result.PolicyQueryResult;
import com.titanium.policy.web.dto.member.AddInsuredMemberDTO;
import com.titanium.policy.web.response.policy.PolicyDetailVO;

class PolicyWebMapperTest {

    private final PolicyWebMapper mapper = Mappers.getMapper(PolicyWebMapper.class);

    @Test
    void mapsPolicyDetailFieldsAndUsesProductIdForApiResponse() {
        PolicyQueryResult result = new PolicyQueryResult();
        result.setProposalId("proposal-1");
        result.setUnderwritingId("underwriting-1");
        result.setMarketPackageId("package-1");
        result.setProductId("product-id-1");
        result.setProductCode("product-code-1");
        result.setTotalPremium(new BigDecimal("1200.00"));
        result.setLineCount(2);
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
        assertEquals("ONLINE", detail.getCollectionMode());
        assertEquals("COLLECTED", detail.getCollectionStatus());
        assertEquals(new BigDecimal("1200.00"), detail.getCollectedAmount());
        assertEquals("channel-1", detail.getChannelId());
        assertEquals("AGENT", detail.getSalesChannel());
        assertEquals("agent-1", detail.getAgentId());
        assertEquals("product-id-1", response.getProductId());
    }

    @Test
    void mapsMemberDtoToCommandWithDirectFamilyRelation() {
        AddInsuredMemberDTO dto = new AddInsuredMemberDTO();
        dto.setInsuredId("INSURED_001");
        dto.setCustomerId("CUSTOMER_001");
        dto.setName("张三");
        dto.setCertType(IdCardType.CHINA_ID_CARD);
        dto.setCertNo("110101199001011234");
        dto.setAge(35);
        dto.setGender(CustomerGender.MALE);
        dto.setPhone("13800138000");
        dto.setRelationToHolder("SELF");
        dto.setFamilyRelation(FamilyRelation.CHILD);

        AddInsuredMemberCommand command = mapper.toAddInsuredMemberCommand(dto, "POLICY_001", "operator-1",
                "tenant-1");

        assertEquals("POLICY_001", command.policyId());
        assertEquals("INSURED_001", command.member().insuredId());
        assertEquals("CUSTOMER_001", command.member().customerId());
        assertEquals(35, command.member().age());
        assertEquals(FamilyRelation.CHILD, command.member().familyRelation());
        assertEquals(FamilyRelation.CHILD, command.familyRelation());
        assertEquals("operator-1", command.operatorId());
        assertEquals("tenant-1", command.tenantId());
    }

    @Test
    void mapsMemberRequestToCommandTranslatingFamilyRelationCode() {
        AddInsuredMemberRequest request = new AddInsuredMemberRequest("INSURED_002", "CUSTOMER_002", "李四",
                IdCardType.CHINA_ID_CARD, "110101199202022222", 30, CustomerGender.FEMALE, "13900139000",
                "SPOUSE", "SPOUSE");

        AddInsuredMemberCommand command = mapper.toAddInsuredMemberCommand(request, "POLICY_001", "operator-2",
                "tenant-1");

        // api 契约以 code 字符串承载家庭关系，翻译点固定在 web 层映射器
        assertEquals(FamilyRelation.SPOUSE, command.familyRelation());
        assertEquals(FamilyRelation.SPOUSE, command.member().familyRelation());
        assertEquals("INSURED_002", command.member().insuredId());
        assertEquals(30, command.member().age());
        assertEquals("operator-2", command.operatorId());
        assertEquals("tenant-1", command.tenantId());
    }

    @Test
    void mapsRemoveMemberCommandFromScalarsWithOptionalReason() {
        RemoveInsuredMemberCommand command = mapper.toRemoveInsuredMemberCommand(
                "POLICY_001", "INSURED_001", null, "operator-1", "tenant-1");

        assertEquals("POLICY_001", command.policyId());
        assertEquals("INSURED_001", command.insuredId());
        assertNull(command.reason());
        assertEquals("operator-1", command.operatorId());
        assertEquals("tenant-1", command.tenantId());
    }
}
