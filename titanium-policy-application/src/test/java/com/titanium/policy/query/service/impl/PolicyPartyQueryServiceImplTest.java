package com.titanium.policy.query.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.titanium.policy.query.repository.PolicyBeneficiaryViewRepository;
import com.titanium.policy.query.repository.PolicyInsuredViewRepository;
import com.titanium.policy.query.result.PolicyBeneficiaryQueryResult;
import com.titanium.policy.query.view.PolicyBeneficiaryView;

class PolicyPartyQueryServiceImplTest {

    @Test
    void exposesBeneficiaryGenderAndPhoneFromSnapshot() {
        PolicyInsuredViewRepository insuredRepository = mock(PolicyInsuredViewRepository.class);
        PolicyBeneficiaryViewRepository beneficiaryRepository = mock(PolicyBeneficiaryViewRepository.class);
        PolicyBeneficiaryView view = new PolicyBeneficiaryView();
        view.setPolicyId("POLICY_001");
        view.setTenantId("TENANT_001");
        view.setGender("FEMALE");
        view.setPhone("13900139000");
        when(beneficiaryRepository.findByPolicyIdAndTenantId("POLICY_001", "TENANT_001"))
                .thenReturn(List.of(view));

        PolicyBeneficiaryQueryResult result = new PolicyPartyQueryServiceImpl(insuredRepository,
                beneficiaryRepository).findBeneficiariesByPolicyId("POLICY_001", "TENANT_001").getFirst();

        assertEquals("FEMALE", result.getGender());
        assertEquals("13900139000", result.getPhone());
    }
}
