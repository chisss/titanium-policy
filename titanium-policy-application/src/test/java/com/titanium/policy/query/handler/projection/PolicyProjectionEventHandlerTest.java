package com.titanium.policy.query.handler.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.metadata.enums.maintenance.PolicyMaintenanceAction;
import com.titanium.metadata.enums.policy.PolicyEnum;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.policy.common.enums.EndorsementCategory;
import com.titanium.policy.common.enums.PolicyStatusCode;
import com.titanium.policy.entity.policy.PolicyProduct;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.event.PolicyMaintenanceStateAppliedEvent;
import com.titanium.policy.query.mapper.PolicyViewMapper;
import com.titanium.policy.query.repository.PolicyViewRepository;
import com.titanium.policy.query.view.PolicyView;

class PolicyProjectionEventHandlerTest {

    @Test
    void projectsPolicyProductCountIntoPolicyView() {
        PolicyViewRepository repository = mock(PolicyViewRepository.class);
        PolicyViewMapper mapper = mock(PolicyViewMapper.class);
        when(repository.findByPolicyIdAndTenantId("POLICY_001", "TENANT_001"))
                .thenReturn(Optional.of(new PolicyView()));
        PolicyProduct mainProduct = policyProduct("LINE_001", 1, ProductCategory.MAIN, "MAIN_CODE");
        PolicyProduct riderProduct = policyProduct("LINE_002", 2, ProductCategory.RIDER, "RIDER_CODE");
        PolicyCreatedEvent event = new PolicyCreatedEvent("POLICY_001", null, null, null, null, null, null,
                "BIZ_001", null, null, null, null, List.of(mainProduct, riderProduct), null, null, null, null, null,
                null, "TENANT_001");

        new PolicyProjectionEventHandler(repository, mapper).on(event);

        ArgumentCaptor<PolicyView> captor = ArgumentCaptor.forClass(PolicyView.class);
        verify(repository).save(captor.capture());
        verify(mapper).applyCreated(any(PolicyView.class), any(PolicyCreatedEvent.class));
        assertEquals(2, captor.getValue().getLineCount());
        assertEquals("MAIN_CODE", captor.getValue().getProductCode());
        assertEquals(0, captor.getValue().getCurrentVersion());
    }

    @Test
    void infersOneLineForHistoricalEventWithOnlyMainProductId() {
        PolicyViewRepository repository = mock(PolicyViewRepository.class);
        PolicyViewMapper mapper = mock(PolicyViewMapper.class);
        when(repository.findByPolicyIdAndTenantId("POLICY_001", "TENANT_001"))
                .thenReturn(Optional.of(new PolicyView()));
        PolicyCreatedEvent event = new PolicyCreatedEvent("POLICY_001", null, null, "PRODUCT_001", null, null, null,
                "BIZ_001", null, null, null, null, null, null, null, null, null, null, null, "TENANT_001");

        new PolicyProjectionEventHandler(repository, mapper).on(event);

        ArgumentCaptor<PolicyView> captor = ArgumentCaptor.forClass(PolicyView.class);
        verify(repository).save(captor.capture());
        assertEquals(1, captor.getValue().getLineCount());
    }

    @Test
    void preservesExistingLineCountWhenHistoricalEventCannotInferIt() {
        PolicyViewRepository repository = mock(PolicyViewRepository.class);
        PolicyViewMapper mapper = mock(PolicyViewMapper.class);
        PolicyView existing = new PolicyView();
        existing.setLineCount(3);
        when(repository.findByPolicyIdAndTenantId("POLICY_001", "TENANT_001"))
                .thenReturn(Optional.of(existing));
        PolicyCreatedEvent event = new PolicyCreatedEvent("POLICY_001", null, null, null, null, null, null,
                "BIZ_001", null, null, null, null, null, null, null, null, null, null, null, "TENANT_001");

        new PolicyProjectionEventHandler(repository, mapper).on(event);

        ArgumentCaptor<PolicyView> captor = ArgumentCaptor.forClass(PolicyView.class);
        verify(repository).save(captor.capture());
        assertEquals(3, captor.getValue().getLineCount());
    }

    @Test
    void preservesCurrentVersionWhenCreatedEventIsReplayed() {
        PolicyViewRepository repository = mock(PolicyViewRepository.class);
        PolicyViewMapper mapper = mock(PolicyViewMapper.class);
        PolicyView existing = new PolicyView();
        existing.setCurrentVersion(3);
        when(repository.findByPolicyIdAndTenantId("POLICY_001", "TENANT_001"))
                .thenReturn(Optional.of(existing));
        PolicyCreatedEvent event = new PolicyCreatedEvent("POLICY_001", null, null, null, null, null, null,
                "BIZ_001", null, null, null, null, null,
                List.of(policyProduct("LINE_001", 1, ProductCategory.MAIN, "MAIN_CODE")),
                null, null, null, null, null, null, "TENANT_001");

        new PolicyProjectionEventHandler(repository, mapper).on(event);

        ArgumentCaptor<PolicyView> captor = ArgumentCaptor.forClass(PolicyView.class);
        verify(repository).save(captor.capture());
        assertEquals(3, captor.getValue().getCurrentVersion());
    }

    /**
     * 🔴 G11/g11-04 D1：多主险（交强 + 商业同单）下保单级产品编码**保非空**——
     * 按 lineNo 升序取**首个有编码的主险段**；旧行为「主险不唯一即不填」在方案 B 下会让
     * 列表/筛选列整体变空。
     */
    @Test
    void picksFirstCodedMainProductWhenCreatedEventContainsMultipleMainProducts() {
        PolicyViewRepository repository = mock(PolicyViewRepository.class);
        PolicyViewMapper mapper = mock(PolicyViewMapper.class);
        PolicyView existing = new PolicyView();
        existing.setProductCode("EXISTING_CODE");
        when(repository.findByPolicyIdAndTenantId("POLICY_001", "TENANT_001"))
                .thenReturn(Optional.of(existing));
        PolicyCreatedEvent event = new PolicyCreatedEvent("POLICY_001", null, null, null, null, null, null,
                "BIZ_001", null, null, null, null, null,
                List.of(policyProduct("LINE_001", 1, ProductCategory.MAIN, "MAIN_CODE_1"),
                        policyProduct("LINE_002", 2, ProductCategory.MAIN, "MAIN_CODE_2")),
                null, null, null, null, null, null, "TENANT_001");

        new PolicyProjectionEventHandler(repository, mapper).on(event);

        ArgumentCaptor<PolicyView> captor = ArgumentCaptor.forClass(PolicyView.class);
        verify(repository).save(captor.capture());
        assertEquals("MAIN_CODE_1", captor.getValue().getProductCode());
    }

    /** 🔴 D1 判据是 lineNo 而非事件列表顺序：列表逆序给出时仍取 lineNo 最小的有编码主险段。 */
    @Test
    void picksFirstCodedMainProductRegardlessOfEventListOrder() {
        PolicyViewRepository repository = mock(PolicyViewRepository.class);
        PolicyViewMapper mapper = mock(PolicyViewMapper.class);
        when(repository.findByPolicyIdAndTenantId("POLICY_001", "TENANT_001"))
                .thenReturn(Optional.of(new PolicyView()));
        PolicyCreatedEvent event = new PolicyCreatedEvent("POLICY_001", null, null, null, null, null, null,
                "BIZ_001", null, null, null, null, null,
                List.of(policyProduct("LINE_002", 2, ProductCategory.MAIN, "MAIN_CODE_2"),
                        policyProduct("LINE_001", 1, ProductCategory.MAIN, "MAIN_CODE_1")),
                null, null, null, null, null, null, "TENANT_001");

        new PolicyProjectionEventHandler(repository, mapper).on(event);

        ArgumentCaptor<PolicyView> captor = ArgumentCaptor.forClass(PolicyView.class);
        verify(repository).save(captor.capture());
        assertEquals("MAIN_CODE_1", captor.getValue().getProductCode());
    }

    /** 🔴 D1「保非空」：首个主险段（按 lineNo）无编码时顺延取下一个有编码的主险段，不得因首段为空整体不填。 */
    @Test
    void skipsUncodedMainLineWhenDerivingProductCode() {
        PolicyViewRepository repository = mock(PolicyViewRepository.class);
        PolicyViewMapper mapper = mock(PolicyViewMapper.class);
        when(repository.findByPolicyIdAndTenantId("POLICY_001", "TENANT_001"))
                .thenReturn(Optional.of(new PolicyView()));
        PolicyCreatedEvent event = new PolicyCreatedEvent("POLICY_001", null, null, null, null, null, null,
                "BIZ_001", null, null, null, null, null,
                List.of(policyProduct("LINE_001", 1, ProductCategory.MAIN, null),
                        policyProduct("LINE_002", 2, ProductCategory.MAIN, "MAIN_CODE_2")),
                null, null, null, null, null, null, "TENANT_001");

        new PolicyProjectionEventHandler(repository, mapper).on(event);

        ArgumentCaptor<PolicyView> captor = ArgumentCaptor.forClass(PolicyView.class);
        verify(repository).save(captor.capture());
        assertEquals("MAIN_CODE_2", captor.getValue().getProductCode());
    }

    /** 主险段全部无编码时保留既有值——派生列只做填充，不得把已有值清空。 */
    @Test
    void preservesExistingCodeWhenNoMainLineCarriesCode() {
        PolicyViewRepository repository = mock(PolicyViewRepository.class);
        PolicyViewMapper mapper = mock(PolicyViewMapper.class);
        PolicyView existing = new PolicyView();
        existing.setProductCode("EXISTING_CODE");
        when(repository.findByPolicyIdAndTenantId("POLICY_001", "TENANT_001"))
                .thenReturn(Optional.of(existing));
        PolicyCreatedEvent event = new PolicyCreatedEvent("POLICY_001", null, null, null, null, null, null,
                "BIZ_001", null, null, null, null, null,
                List.of(policyProduct("LINE_001", 1, ProductCategory.MAIN, null),
                        policyProduct("LINE_002", 2, ProductCategory.MAIN, null)),
                null, null, null, null, null, null, "TENANT_001");

        new PolicyProjectionEventHandler(repository, mapper).on(event);

        ArgumentCaptor<PolicyView> captor = ArgumentCaptor.forClass(PolicyView.class);
        verify(repository).save(captor.capture());
        assertEquals("EXISTING_CODE", captor.getValue().getProductCode());
    }

    /** 附加险段编码不参与派生——派生列的语义是「首主险段编码」，不得被附加险误填。 */
    @Test
    void doesNotDeriveProductCodeFromRiderLine() {
        PolicyViewRepository repository = mock(PolicyViewRepository.class);
        PolicyViewMapper mapper = mock(PolicyViewMapper.class);
        when(repository.findByPolicyIdAndTenantId("POLICY_001", "TENANT_001"))
                .thenReturn(Optional.of(new PolicyView()));
        PolicyCreatedEvent event = new PolicyCreatedEvent("POLICY_001", null, null, null, null, null, null,
                "BIZ_001", null, null, null, null, null,
                List.of(policyProduct("LINE_001", 1, ProductCategory.MAIN, "MAIN_CODE"),
                        policyProduct("LINE_002", 2, ProductCategory.RIDER, "RIDER_CODE")),
                null, null, null, null, null, null, "TENANT_001");

        new PolicyProjectionEventHandler(repository, mapper).on(event);

        ArgumentCaptor<PolicyView> captor = ArgumentCaptor.forClass(PolicyView.class);
        verify(repository).save(captor.capture());
        assertEquals("MAIN_CODE", captor.getValue().getProductCode());
    }

    @Test
    void projectsMaintenanceStateEventIntoPolicyStatus() {
        PolicyViewRepository repository = mock(PolicyViewRepository.class);
        PolicyViewMapper mapper = mock(PolicyViewMapper.class);
        when(repository.findByPolicyIdAndTenantId("POLICY_001", "TENANT_001"))
                .thenReturn(Optional.of(new PolicyView()));

        new PolicyProjectionEventHandler(repository, mapper).on(stateEvent());

        ArgumentCaptor<PolicyView> captor = ArgumentCaptor.forClass(PolicyView.class);
        verify(repository).save(captor.capture());
        assertEquals(PolicyEnum.PolicyStatus.SUSPENDED, captor.getValue().getPolicyStatus());
    }

    private PolicyMaintenanceStateAppliedEvent stateEvent() {
        LocalDateTime appliedAt = LocalDateTime.parse("2026-08-25T16:00:00");
        return new PolicyMaintenanceStateAppliedEvent(
                "POLICY_001", "REQUEST_001", "a".repeat(64), "MAINTENANCE_001", "END_001",
                "POLICY_SUSPENSION", EndorsementCategory.LIFECYCLE, 7, 8, appliedAt,
                "保单暂停", "b".repeat(64), "c".repeat(64), "axon-event://policy/POLICY_001/8",
                "d".repeat(64), "e".repeat(64), List.of(), null, PolicyMaintenanceAction.SUSPEND,
                PolicyStatusCode.EFFECTIVE, PolicyStatusCode.SUSPENDED,
                "客户申请暂停", null, appliedAt, "operator-1", "TENANT_001");
    }

    private PolicyProduct policyProduct(String policyProductId, int lineNo, ProductCategory category,
                                        String productCode) {
        return new PolicyProduct(policyProductId, lineNo, category, null, null, productCode, null, null, null, null,
                null, null, null, null, null, List.of(), List.of(), List.of());
    }
}
