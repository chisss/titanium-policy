package com.titanium.policy.query.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.titanium.common.exception.BusinessException;
import com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.policy.query.repository.InsuranceViewRepository;
import com.titanium.policy.query.repository.PolicyInsuredViewRepository;
import com.titanium.policy.query.repository.PolicyProductViewRepository;
import com.titanium.policy.query.repository.PolicyViewRepository;
import com.titanium.policy.query.result.PolicyQueryResult;
import com.titanium.policy.query.view.InsuranceView;
import com.titanium.policy.query.view.PolicyInsuredView;
import com.titanium.policy.query.view.PolicyProductView;
import com.titanium.policy.query.view.PolicyView;

class PolicyQueryServiceImplTest {

    private static final String POLICY_ID = "policy-1";
    private static final String TENANT_ID = "tenant-1";

    @Test
    void mapsProjectedPolicyDetailFields() throws Exception {
        PolicyView view = new PolicyView();
        LocalDateTime waitingPeriodEndDate = LocalDateTime.of(2026, 8, 20, 0, 0);
        LocalDateTime hesitationPeriodEndDate = LocalDateTime.of(2026, 8, 27, 0, 0);
        view.setProposalId("proposal-1");
        view.setUnderwritingId("underwriting-1");
        view.setMarketPackageId("package-1");
        view.setProductId("product-id-1");
        view.setSumInsured(new BigDecimal("100000.00"));
        view.setTotalPremium(new BigDecimal("1200.00"));
        view.setLineCount(2);
        view.setWaitingPeriodEndDate(waitingPeriodEndDate);
        view.setHesitationPeriodEndDate(hesitationPeriodEndDate);
        view.setCollectionMode("ONLINE");
        view.setCollectionStatus("COLLECTED");
        view.setCollectedAmount(new BigDecimal("1200.00"));
        view.setChannelId("channel-1");
        view.setSalesChannel("AGENT");
        view.setAgentId("agent-1");

        PolicyQueryResult result = invokeToQueryResult(view);

        assertEquals("proposal-1", result.getProposalId());
        assertEquals("underwriting-1", result.getUnderwritingId());
        assertEquals("package-1", result.getMarketPackageId());
        assertEquals("product-id-1", result.getProductId());
        assertEquals(100000.00, result.getSumInsured());
        assertEquals(new BigDecimal("1200.00"), result.getTotalPremium());
        assertEquals(2, result.getLineCount());
        assertEquals(waitingPeriodEndDate, result.getWaitingPeriodEndDate());
        assertEquals(hesitationPeriodEndDate, result.getHesitationPeriodEndDate());
        assertEquals("ONLINE", result.getCollectionMode());
        assertEquals("COLLECTED", result.getCollectionStatus());
        assertEquals(new BigDecimal("1200.00"), result.getCollectedAmount());
        assertEquals("channel-1", result.getChannelId());
        assertEquals("AGENT", result.getSalesChannel());
        assertEquals("agent-1", result.getAgentId());
    }

    @Test
    void enrichesPolicyDetailFromExistingSnapshotViews() {
        PolicyViewRepository policyRepository = Mockito.mock(PolicyViewRepository.class);
        PolicyProductViewRepository productRepository = Mockito.mock(PolicyProductViewRepository.class);
        PolicyInsuredViewRepository insuredRepository = Mockito.mock(PolicyInsuredViewRepository.class);
        InsuranceViewRepository insuranceRepository = Mockito.mock(InsuranceViewRepository.class);

        PolicyView view = new PolicyView();
        view.setPolicyId(POLICY_ID);
        view.setInsuranceId("insurance-1");
        view.setTenantId(TENANT_ID);
        when(policyRepository.findByPolicyIdAndTenantId(POLICY_ID, TENANT_ID)).thenReturn(Optional.of(view));

        PolicyProductView product = new PolicyProductView();
        product.setProductCategory("MAIN");
        product.setProductName("测试定期寿险");
        when(productRepository.findByPolicyIdAndTenantIdOrderByLineNoAsc(POLICY_ID, TENANT_ID))
                .thenReturn(List.of(product));

        PolicyInsuredView insured = new PolicyInsuredView();
        insured.setCustomerId("customer-1");
        when(insuredRepository.findByPolicyIdAndTenantId(POLICY_ID, TENANT_ID)).thenReturn(List.of(insured));

        InsuranceView insurance = new InsuranceView();
        insurance.setPolicyForm(PolicyForm.INDIVIDUAL);
        when(insuranceRepository.findByInsuranceIdAndTenantId("insurance-1", TENANT_ID))
                .thenReturn(Optional.of(insurance));

        PolicyQueryResult result = new PolicyQueryServiceImpl(policyRepository, productRepository, insuredRepository,
                insuranceRepository).findPolicyById(POLICY_ID, TENANT_ID);

        assertEquals("测试定期寿险", result.getProductName());
        assertEquals("customer-1", result.getInsuredId());
        assertEquals(PolicyForm.INDIVIDUAL, result.getPolicyForm());
    }

    @Test
    void buildsStableTenantScopedMaintenanceSnapshot() {
        PolicyViewRepository policyRepository = Mockito.mock(PolicyViewRepository.class);
        PolicyProductViewRepository productRepository = Mockito.mock(PolicyProductViewRepository.class);
        PolicyView policy = maintenancePolicyView();
        PolicyProductView product = maintenanceMainProduct();
        when(policyRepository.findByPolicyIdAndTenantId(POLICY_ID, TENANT_ID)).thenReturn(Optional.of(policy));
        when(productRepository.findByPolicyIdAndTenantIdOrderByLineNoAsc(POLICY_ID, TENANT_ID))
                .thenReturn(List.of(product));
        PolicyQueryServiceImpl service = new PolicyQueryServiceImpl(
                policyRepository, productRepository, null, null);

        var first = service.findMaintenanceSnapshot(POLICY_ID, TENANT_ID);
        var second = service.findMaintenanceSnapshot(POLICY_ID, TENANT_ID);

        assertEquals(7L, first.policyVersion());
        assertEquals("product-v3", first.productVersion());
        assertEquals("plan-v8", first.planVersion());
        assertEquals(first.snapshotContentHash(), second.snapshotContentHash());
        assertEquals(64, first.snapshotContentHash().length());
        assertNotNull(first.fieldValues().get("policy.holder.mobile"));
        assertEquals("policy-product-1",
                first.fieldValues().get("policy.coverage.sumInsured").objectId());
        assertNotNull(first.nextBillingDateAt());
        assertNotNull(first.nextPolicyAnniversaryAt());
    }

    @Test
    void rejectsMaintenanceSnapshotWithoutPricingPlanVersion() {
        PolicyViewRepository policyRepository = Mockito.mock(PolicyViewRepository.class);
        PolicyProductViewRepository productRepository = Mockito.mock(PolicyProductViewRepository.class);
        PolicyProductView product = maintenanceMainProduct();
        product.setPricingPlanVersion(null);
        when(policyRepository.findByPolicyIdAndTenantId(POLICY_ID, TENANT_ID))
                .thenReturn(Optional.of(maintenancePolicyView()));
        when(productRepository.findByPolicyIdAndTenantIdOrderByLineNoAsc(POLICY_ID, TENANT_ID))
                .thenReturn(List.of(product));

        assertThrows(BusinessException.class, () -> new PolicyQueryServiceImpl(
                policyRepository, productRepository, null, null)
                .findMaintenanceSnapshot(POLICY_ID, TENANT_ID));
    }

    private PolicyView maintenancePolicyView() {
        PolicyView view = new PolicyView();
        view.setPolicyId(POLICY_ID);
        view.setPolicyNo("P202608240001");
        view.setPolicyHolderId("customer-1");
        view.setPolicyHolderName("张三");
        view.setPolicyHolderPhone("13800000000");
        view.setPolicyStatus(PolicyStatus.EFFECTIVE);
        view.setCurrentVersion(7);
        view.setStartDate(LocalDateTime.of(2026, 8, 1, 0, 0));
        view.setEndDate(LocalDateTime.of(2036, 8, 1, 0, 0));
        view.setTotalPremium(new BigDecimal("1200.00"));
        view.setCollectionMode("ONLINE");
        view.setTenantId(TENANT_ID);
        return view;
    }

    private PolicyProductView maintenanceMainProduct() {
        PolicyProductView view = new PolicyProductView();
        view.setPolicyProductId("policy-product-1");
        view.setProductCategory("MAIN");
        view.setProductId("product-1");
        view.setProductVersion("product-v3");
        view.setPricingPlanVersion("plan-v8");
        view.setPaymentFrequency("ANNUAL");
        view.setPremiumPaymentYears(10);
        view.setSumInsured(new BigDecimal("100000"));
        view.setCurrency("CNY");
        return view;
    }

    private PolicyQueryResult invokeToQueryResult(PolicyView view) throws Exception {
        PolicyQueryServiceImpl service = new PolicyQueryServiceImpl(null, null, null, null);
        Method method = PolicyQueryServiceImpl.class.getDeclaredMethod("toQueryResult", PolicyView.class);
        method.setAccessible(true);
        return (PolicyQueryResult) method.invoke(service, view);
    }

}
