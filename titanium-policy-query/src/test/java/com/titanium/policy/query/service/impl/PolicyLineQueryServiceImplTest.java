package com.titanium.policy.query.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.Pageable;

import com.titanium.metadata.enums.customer.CustomerEnum.InsuranceRole;
import com.titanium.metadata.enums.policy.PolicyEnum;
import com.titanium.policy.query.mapper.PolicyLineQueryMapper;
import com.titanium.policy.query.repository.PolicyBeneficiaryViewRepository;
import com.titanium.policy.query.repository.PolicyInsuredViewRepository;
import com.titanium.policy.query.repository.PolicyProductViewRepository;
import com.titanium.policy.query.repository.PolicyViewRepository;
import com.titanium.policy.query.result.PolicyQueryResult;
import com.titanium.policy.query.view.PolicyBeneficiaryView;
import com.titanium.policy.query.view.PolicyInsuredView;
import com.titanium.policy.query.view.PolicyProductView;
import com.titanium.policy.query.view.PolicyView;

class PolicyLineQueryServiceImplTest {

    private static final String CUSTOMER_ID = "customer-1";
    private static final String POLICY_ID = "policy-1";
    private static final String TENANT_ID = "tenant-1";

    private PolicyViewRepository policyViewRepository;
    private PolicyProductViewRepository policyProductViewRepository;
    private PolicyInsuredViewRepository policyInsuredViewRepository;
    private PolicyBeneficiaryViewRepository policyBeneficiaryViewRepository;
    private PolicyLineQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        policyViewRepository = mock(PolicyViewRepository.class);
        policyProductViewRepository = mock(PolicyProductViewRepository.class);
        policyInsuredViewRepository = mock(PolicyInsuredViewRepository.class);
        policyBeneficiaryViewRepository = mock(PolicyBeneficiaryViewRepository.class);
        PolicyLineQueryMapper mapper = Mappers.getMapper(PolicyLineQueryMapper.class);
        service = new PolicyLineQueryServiceImpl(policyViewRepository, policyProductViewRepository, null, null, null,
                null, policyInsuredViewRepository, policyBeneficiaryViewRepository, mapper);
    }

    @Test
    void returnsMappedPolicyForEveryCustomerRole() {
        PolicyView view = policyView();
        PolicyInsuredView insured = new PolicyInsuredView();
        insured.setPolicyId(POLICY_ID);
        PolicyBeneficiaryView beneficiary = new PolicyBeneficiaryView();
        beneficiary.setPolicyId(POLICY_ID);
        when(policyViewRepository.findByPolicyHolderIdAndTenantId(CUSTOMER_ID, TENANT_ID, Pageable.unpaged()))
                .thenReturn(List.of(view));
        when(policyInsuredViewRepository.findByCustomerIdAndTenantId(CUSTOMER_ID, TENANT_ID))
                .thenReturn(List.of(insured));
        when(policyBeneficiaryViewRepository.findByCustomerIdAndTenantId(CUSTOMER_ID, TENANT_ID))
                .thenReturn(List.of(beneficiary));
        when(policyViewRepository.findByPolicyIdAndTenantId(POLICY_ID, TENANT_ID)).thenReturn(Optional.of(view));
        when(policyProductViewRepository.findByPolicyIdAndTenantIdOrderByLineNoAsc(POLICY_ID, TENANT_ID))
                .thenReturn(List.of());

        for (InsuranceRole role : InsuranceRole.values()) {
            List<PolicyQueryResult> results = service.findByCustomerRole(CUSTOMER_ID, role, TENANT_ID, 0, 10);

            assertEquals(1, results.size());
            assertEquals(PolicyEnum.PolicyStatus.EFFECTIVE, results.getFirst().getStatus());
            assertEquals(view.getStartDate(), results.getFirst().getEffectiveDate());
            assertEquals(view.getEndDate(), results.getFirst().getExpiryDate());
            assertEquals(view.getInsuranceId(), results.getFirst().getApplicationId());
        }
    }

    @Test
    void enrichesProductDisplayFieldsFromMainProductSnapshot() {
        PolicyView view = policyView();
        PolicyProductView rider = product("RIDER", "RIDER-001", "附加险");
        PolicyProductView main = product("MAIN", "MAIN-001", "主险产品");
        stubPolicyHolderQuery(view, List.of(rider, main));

        PolicyQueryResult result = service
                .findByCustomerRole(CUSTOMER_ID, InsuranceRole.POLICY_HOLDER, TENANT_ID, 0, 10)
                .getFirst();

        assertEquals("主险产品", result.getProductName());
        assertEquals("MAIN-001", result.getProductCode());
    }

    @Test
    void usesFirstSnapshotWhenLegacyDataHasNoMainCategory() {
        PolicyView view = policyView();
        PolicyProductView first = product(null, "LEGACY-001", "历史险种");
        PolicyProductView second = product("RIDER", "RIDER-001", "附加险");
        stubPolicyHolderQuery(view, List.of(first, second));

        PolicyQueryResult result = service
                .findByCustomerRole(CUSTOMER_ID, InsuranceRole.POLICY_HOLDER, TENANT_ID, 0, 10)
                .getFirst();

        assertEquals("历史险种", result.getProductName());
        assertEquals("LEGACY-001", result.getProductCode());
    }

    @Test
    void keepsPolicyWhenProductSnapshotIsMissing() {
        PolicyView view = policyView();
        stubPolicyHolderQuery(view, List.of());

        PolicyQueryResult result = service
                .findByCustomerRole(CUSTOMER_ID, InsuranceRole.POLICY_HOLDER, TENANT_ID, 0, 10)
                .getFirst();

        assertEquals(PolicyEnum.PolicyStatus.EFFECTIVE, result.getStatus());
        assertNull(result.getProductName());
    }

    private void stubPolicyHolderQuery(PolicyView view, List<PolicyProductView> products) {
        when(policyViewRepository.findByPolicyHolderIdAndTenantId(CUSTOMER_ID, TENANT_ID, Pageable.unpaged()))
                .thenReturn(List.of(view));
        when(policyViewRepository.findByPolicyIdAndTenantId(POLICY_ID, TENANT_ID)).thenReturn(Optional.of(view));
        when(policyProductViewRepository.findByPolicyIdAndTenantIdOrderByLineNoAsc(POLICY_ID, TENANT_ID))
                .thenReturn(products);
    }

    private PolicyView policyView() {
        PolicyView view = new PolicyView();
        view.setPolicyId(POLICY_ID);
        view.setInsuranceId("application-1");
        view.setPolicyStatus(PolicyEnum.PolicyStatus.EFFECTIVE);
        view.setStartDate(LocalDateTime.of(2026, 8, 14, 0, 0));
        view.setEndDate(LocalDateTime.of(2027, 8, 13, 23, 59));
        return view;
    }

    private PolicyProductView product(String category, String code, String name) {
        PolicyProductView product = new PolicyProductView();
        product.setProductCategory(category);
        product.setProductCode(code);
        product.setProductName(name);
        return product;
    }
}
