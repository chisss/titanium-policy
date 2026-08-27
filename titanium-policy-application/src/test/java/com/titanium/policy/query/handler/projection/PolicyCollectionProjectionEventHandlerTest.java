package com.titanium.policy.query.handler.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.policy.common.enums.PremiumCollectionStatus;
import com.titanium.policy.event.PremiumBillingAssociatedEvent;
import com.titanium.policy.query.repository.PolicyCollectionViewRepository;
import com.titanium.policy.query.repository.PolicyViewRepository;
import com.titanium.policy.query.view.PolicyCollectionView;

/**
 * 收费单据关联投影测试。
 */
class PolicyCollectionProjectionEventHandlerTest {

    private PolicyCollectionViewRepository collectionRepository;
    private PolicyCollectionProjectionEventHandler handler;

    @BeforeEach
    void setUp() {
        collectionRepository = mock(PolicyCollectionViewRepository.class);
        handler = new PolicyCollectionProjectionEventHandler(collectionRepository, mock(PolicyViewRepository.class));
    }

    @Test
    void billingAssociationBackfillsDocumentIdentifiers() {
        PolicyCollectionView view = new PolicyCollectionView();
        view.setId("POLICY_001");
        view.setPolicyId("POLICY_001");
        when(collectionRepository.findById("POLICY_001")).thenReturn(Optional.of(view));

        handler.on(new PremiumBillingAssociatedEvent("POLICY_001", "BIZ_001", "BILL_001", "PAY_001",
                PremiumCollectionStatus.UNCOLLECTED, "TENANT_001"));

        assertEquals("BILL_001", view.getBillId());
        assertEquals("PAY_001", view.getPaymentOrderId());
        assertEquals("UNCOLLECTED", view.getCollectionStatus());
        verify(collectionRepository).save(view);
    }
}
