package com.titanium.policy.web.assembler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.customer.CustomerEnum.CustomerGender;
import com.titanium.policy.api.request.SubmitIssuanceRequest;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.valueobject.IssuanceRequest;

class IssuanceRequestAssemblerTest {

    @Test
    void preservesBeneficiaryGenderAndMobileInDomainSnapshot() {
        SubmitIssuanceRequest.PartyInput beneficiary = new SubmitIssuanceRequest.PartyInput();
        beneficiary.setName("李四");
        beneficiary.setCertType("CHINA_ID_CARD");
        beneficiary.setCertNo("110101199202022222");
        beneficiary.setGender("FEMALE");
        beneficiary.setMobile("13900139000");
        beneficiary.setBeneficiaryType("DEATH");
        beneficiary.setBeneficiaryOrder(1);
        beneficiary.setShareRatio(new BigDecimal("100"));

        SubmitIssuanceRequest request = new SubmitIssuanceRequest();
        request.setBizNo("BIZ_001");
        request.setBeneficiaryList(List.of(beneficiary));

        IssuanceRequest result = new IssuanceRequestAssembler().toDomainRequest(request, "TENANT_001");

        InsuredPartyList.BeneficiaryInfo snapshot = result.insuredPartyList().beneficiaryList().getFirst();
        assertEquals(CustomerGender.FEMALE, snapshot.gender());
        assertEquals("13900139000", snapshot.phone());
    }
}
