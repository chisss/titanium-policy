package com.titanium.policy.query.handler.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.metadata.enums.maintenance.PolicyMaintenanceAction;
import com.titanium.metadata.enums.policy.BeneficiaryType;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.common.enums.EndorsementCategory;
import com.titanium.policy.common.enums.PolicyDataUpdateType;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.entity.policy.PolicyProduct;
import com.titanium.policy.event.PolicyMaintenanceAppliedEvent;
import com.titanium.policy.event.PolicyMaintenanceStateAppliedEvent;
import com.titanium.policy.query.mapper.PolicyViewMapper;
import com.titanium.policy.query.repository.PolicyBeneficiaryViewRepository;
import com.titanium.policy.query.repository.PolicyEndorsementViewRepository;
import com.titanium.policy.query.repository.PolicyProductViewRepository;
import com.titanium.policy.query.repository.PolicyViewRepository;
import com.titanium.policy.query.view.PolicyBeneficiaryView;
import com.titanium.policy.query.view.PolicyEndorsementView;
import com.titanium.policy.query.view.PolicyProductView;
import com.titanium.policy.query.view.PolicyView;
import com.titanium.policy.valueobject.PolicyStatus;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceExecutionState;

class EndorsementProjectionEventHandlerTest {

    @Test
    void projectsMaintenanceStateEventIntoEndorsementAndPolicyVersion() {
        PolicyEndorsementViewRepository endorsementRepository = mock(PolicyEndorsementViewRepository.class);
        PolicyViewRepository policyRepository = mock(PolicyViewRepository.class);
        PolicyProductViewRepository productRepository = mock(PolicyProductViewRepository.class);
        PolicyBeneficiaryViewRepository beneficiaryRepository = mock(PolicyBeneficiaryViewRepository.class);
        PolicyView policy = new PolicyView();
        when(endorsementRepository.findById("END_001")).thenReturn(Optional.empty());
        when(policyRepository.findByPolicyIdAndTenantId("POLICY_001", "TENANT_001"))
                .thenReturn(Optional.of(policy));
        PolicyMaintenanceStateAppliedEvent event = stateEvent();

        new EndorsementProjectionEventHandler(
                endorsementRepository, policyRepository, productRepository, mock(PolicyViewMapper.class),
                beneficiaryRepository).on(event);

        ArgumentCaptor<PolicyEndorsementView> endorsement =
                ArgumentCaptor.forClass(PolicyEndorsementView.class);
        verify(endorsementRepository).save(endorsement.capture());
        verify(policyRepository).save(policy);
        assertEquals("POLICY_SUSPENSION", endorsement.getValue().getUpdateType());
        assertEquals(EndorsementCategory.LIFECYCLE.getCode(), endorsement.getValue().getCategory());
        assertEquals(8, endorsement.getValue().getPolicyVersion());
        assertEquals("MAINTENANCE_001", endorsement.getValue().getSourceMaintenanceId());
        assertEquals(8, policy.getCurrentVersion());
    }

    @Test
    void projectsMaintenanceCoverageIntoPolicyAndProductViews() {
        PolicyEndorsementViewRepository endorsementRepository = mock(PolicyEndorsementViewRepository.class);
        PolicyViewRepository policyRepository = mock(PolicyViewRepository.class);
        PolicyProductViewRepository productRepository = mock(PolicyProductViewRepository.class);
        PolicyBeneficiaryViewRepository beneficiaryRepository = mock(PolicyBeneficiaryViewRepository.class);
        PolicyView policy = new PolicyView();
        PolicyProductView productView = new PolicyProductView();
        productView.setPolicyProductId("LINE_001");
        when(endorsementRepository.findById("END_COVERAGE_001")).thenReturn(Optional.empty());
        when(policyRepository.findByPolicyIdAndTenantId("POLICY_001", "TENANT_001"))
                .thenReturn(Optional.of(policy));
        when(productRepository.findByPolicyIdAndTenantIdOrderByLineNoAsc("POLICY_001", "TENANT_001"))
                .thenReturn(List.of(productView));

        new EndorsementProjectionEventHandler(
                endorsementRepository, policyRepository, productRepository, mock(PolicyViewMapper.class),
                beneficiaryRepository)
                .on(coverageEvent());

        assertEquals(new BigDecimal("120000.00"), policy.getSumInsured());
        assertEquals(new BigDecimal("120000.00"), productView.getSumInsured());
        assertEquals(8, policy.getCurrentVersion());
        verify(productRepository).save(productView);
        verify(policyRepository).save(policy);
    }

    @Test
    void replacesBeneficiaryProjectionFromMaintenanceExecutionState() {
        PolicyEndorsementViewRepository endorsementRepository = mock(PolicyEndorsementViewRepository.class);
        PolicyViewRepository policyRepository = mock(PolicyViewRepository.class);
        PolicyProductViewRepository productRepository = mock(PolicyProductViewRepository.class);
        PolicyBeneficiaryViewRepository beneficiaryRepository = mock(PolicyBeneficiaryViewRepository.class);
        PolicyView policy = new PolicyView();
        when(endorsementRepository.findById("END_BENEFICIARY_001")).thenReturn(Optional.empty());
        when(policyRepository.findByPolicyIdAndTenantId("POLICY_001", "TENANT_001"))
                .thenReturn(Optional.of(policy));

        new EndorsementProjectionEventHandler(
                endorsementRepository, policyRepository, productRepository, mock(PolicyViewMapper.class),
                beneficiaryRepository)
                .on(beneficiaryEvent());

        verify(beneficiaryRepository).deleteByPolicyIdAndTenantId("POLICY_001", "TENANT_001");
        verify(beneficiaryRepository).flush();
        ArgumentCaptor<PolicyBeneficiaryView> beneficiary =
                ArgumentCaptor.forClass(PolicyBeneficiaryView.class);
        verify(beneficiaryRepository).save(beneficiary.capture());
        assertEquals("b".repeat(32), beneficiary.getValue().getId());
        assertEquals("李四", beneficiary.getValue().getBeneficiaryName());
        assertEquals("DEATH", beneficiary.getValue().getBeneficiaryType());
        assertEquals(1, beneficiary.getValue().getOrderNo());
        assertEquals(new BigDecimal("100"), beneficiary.getValue().getShareRatio());
        assertEquals("TENANT_001", beneficiary.getValue().getTenantId());
    }

    private PolicyMaintenanceStateAppliedEvent stateEvent() {
        LocalDateTime appliedAt = LocalDateTime.parse("2026-08-25T16:00:00");
        return new PolicyMaintenanceStateAppliedEvent(
                "POLICY_001", "REQUEST_001", "a".repeat(64), "MAINTENANCE_001", "END_001",
                "POLICY_SUSPENSION", EndorsementCategory.LIFECYCLE, 7, 8, appliedAt,
                "保单暂停", "b".repeat(64), "c".repeat(64), "axon-event://policy/POLICY_001/8",
                "d".repeat(64), "e".repeat(64), List.of(), null, PolicyMaintenanceAction.SUSPEND,
                PolicyStatus.StatusCode.EFFECTIVE, PolicyStatus.StatusCode.SUSPENDED,
                "客户申请暂停", null, appliedAt, "operator-1", "TENANT_001");
    }

    private PolicyMaintenanceAppliedEvent coverageEvent() {
        LocalDateTime appliedAt = LocalDateTime.parse("2026-08-25T16:00:00");
        Money premium = Money.of(new BigDecimal("1000"), "CNY");
        PolicyProduct product = new PolicyProduct(
                "LINE_001", 1, ProductCategory.MAIN, null, "PRODUCT_001", "P001", "测试主险",
                "V1.0", "PLAN_V1", null, Money.of(new BigDecimal("120000"), "CNY"), premium,
                null, null, null, null, List.of(), List.of(), List.of());
        return new PolicyMaintenanceAppliedEvent(
                "POLICY_001", "REQUEST_COVERAGE_001", "a".repeat(64), "MAINTENANCE_COVERAGE_001",
                "END_COVERAGE_001", PolicyDataUpdateType.COVERAGE_AMOUNT_CHANGE,
                EndorsementCategory.SUM_INSURED, 7, 8, appliedAt, "保额变更",
                "b".repeat(64), "c".repeat(64), "axon-event://policy/POLICY_001/8",
                "d".repeat(64), "e".repeat(64), List.of(),
                new PolicyMaintenanceExecutionState(null, List.of(product)),
                appliedAt, "operator-1", "TENANT_001");
    }

    private PolicyMaintenanceAppliedEvent beneficiaryEvent() {
        LocalDateTime appliedAt = LocalDateTime.parse("2026-08-28T10:00:00");
        InsuredPartyList.BeneficiaryInfo beneficiary = new InsuredPartyList.BeneficiaryInfo(
                null, "b".repeat(32), "李四", null, null, null, null, BeneficiaryType.DEATH, 1, 1.0d);
        InsuredPartyList parties = new InsuredPartyList(
                "PARTIES_001", null, List.of(), List.of(beneficiary));
        return new PolicyMaintenanceAppliedEvent(
                "POLICY_001", "REQUEST_BENEFICIARY_001", "a".repeat(64), "MAINTENANCE_BENEFICIARY_001",
                "END_BENEFICIARY_001", PolicyDataUpdateType.BENEFICIARY_CHANGE,
                EndorsementCategory.PARTY, 7, 8, appliedAt, "受益人变更",
                "b".repeat(64), "c".repeat(64), "axon-event://policy/POLICY_001/8",
                "d".repeat(64), "e".repeat(64), List.of(),
                new PolicyMaintenanceExecutionState(parties, null),
                appliedAt, "operator-1", "TENANT_001");
    }
}
