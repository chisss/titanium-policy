package com.titanium.policy.query.handler.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.metadata.enums.customer.CustomerEnum.CustomerGender;
import com.titanium.metadata.enums.customer.CustomerEnum.IdCardType;
import com.titanium.metadata.enums.policy.BeneficiaryType;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.query.repository.PolicyBeneficiaryViewRepository;
import com.titanium.policy.query.repository.PolicyInsuredViewRepository;
import com.titanium.policy.query.view.PolicyBeneficiaryView;
import com.titanium.policy.query.view.PolicyInsuredView;

class PolicyPartyProjectionEventHandlerTest {

    @Test
    void projectsRelationToHolderIntoInsuredSnapshot() {
        PolicyInsuredViewRepository insuredRepository = mock(PolicyInsuredViewRepository.class);
        PolicyBeneficiaryViewRepository beneficiaryRepository = mock(PolicyBeneficiaryViewRepository.class);
        when(insuredRepository.save(any(PolicyInsuredView.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InsuredPartyList.InsuredInfo insured = new InsuredPartyList.InsuredInfo("CUSTOMER_001", "INSURED_001",
                "张三", IdCardType.CHINA_ID_CARD, "110101199001011234", 35, CustomerGender.MALE, "SELF", null);
        InsuredPartyList parties = new InsuredPartyList("PARTIES_001",
                new InsuredPartyList.HolderInfo("CUSTOMER_001", "HOLDER_001", "张三",
                        IdCardType.CHINA_ID_CARD, "110101199001011234", "13800138000"),
                List.of(insured), List.of());
        PolicyCreatedEvent event = policyCreatedEvent(parties);

        new PolicyPartyProjectionEventHandler(insuredRepository, beneficiaryRepository).on(event);

        ArgumentCaptor<PolicyInsuredView> captor = ArgumentCaptor.forClass(PolicyInsuredView.class);
        verify(insuredRepository).save(captor.capture());
        assertEquals("SELF", captor.getValue().getRelation());
        assertEquals("CUSTOMER_001", captor.getValue().getCustomerId());
    }

    @Test
    void projectsBeneficiaryGenderAndPhoneIntoSnapshot() {
        PolicyInsuredViewRepository insuredRepository = mock(PolicyInsuredViewRepository.class);
        PolicyBeneficiaryViewRepository beneficiaryRepository = mock(PolicyBeneficiaryViewRepository.class);
        when(beneficiaryRepository.save(any(PolicyBeneficiaryView.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InsuredPartyList.BeneficiaryInfo beneficiary = new InsuredPartyList.BeneficiaryInfo("CUSTOMER_002",
                "BENEFICIARY_001", "李四", IdCardType.CHINA_ID_CARD, "110101199202022222",
                CustomerGender.FEMALE, "13900139000", BeneficiaryType.DEATH, 1, 1.0d);
        InsuredPartyList parties = new InsuredPartyList("PARTIES_001",
                new InsuredPartyList.HolderInfo("CUSTOMER_001", "HOLDER_001", "张三",
                        IdCardType.CHINA_ID_CARD, "110101199001011234", "13800138000"),
                List.of(), List.of(beneficiary));

        new PolicyPartyProjectionEventHandler(insuredRepository, beneficiaryRepository)
                .on(policyCreatedEvent(parties));

        ArgumentCaptor<PolicyBeneficiaryView> captor = ArgumentCaptor.forClass(PolicyBeneficiaryView.class);
        verify(beneficiaryRepository).save(captor.capture());
        assertEquals("FEMALE", captor.getValue().getGender());
        assertEquals("13900139000", captor.getValue().getPhone());
    }

    private PolicyCreatedEvent policyCreatedEvent(InsuredPartyList parties) {
        return new PolicyCreatedEvent("POLICY_001", null, null, null, null, null, null, "BIZ_001", null, null,
                null, null, List.of(), null, null, null, null, parties, null, "TENANT_001");
    }
}
