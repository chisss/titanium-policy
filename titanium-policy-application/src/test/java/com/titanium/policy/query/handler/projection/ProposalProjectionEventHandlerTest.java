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

import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.metadata.enums.product.ProductEnum.SalesChannel;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.entity.proposal.ProposalLine;
import com.titanium.policy.event.proposal.ProposalCreatedEvent;
import com.titanium.policy.query.mapper.ProposalViewMapper;
import com.titanium.policy.query.mapper.ProposalViewMapperImpl;
import com.titanium.policy.query.repository.ProposalViewRepository;
import com.titanium.policy.query.view.ProposalView;
import com.titanium.policy.valueobject.policy.ChannelInfo;

class ProposalProjectionEventHandlerTest {

    @Test
    void projectsIssuanceTraceAndLineCount() {
        ProposalViewRepository repository = mock(ProposalViewRepository.class);
        ProposalViewMapper mapper = new ProposalViewMapperImpl();
        when(repository.findByProposalIdAndTenantId("PROPOSAL_001", "TENANT_001"))
                .thenReturn(Optional.of(new ProposalView()));
        ProposalLine main = new ProposalLine("LINE_001", 1, ProductCategory.MAIN, null, "PRODUCT_001", "P001",
                InsuranceProductType.WHOLE_LIFE, Money.of(new BigDecimal("500000"), "CNY"),
                Money.of(new BigDecimal("1000"), "CNY"));
        ProposalCreatedEvent event = new ProposalCreatedEvent("PROPOSAL_001", "PRP_001", PolicyForm.INDIVIDUAL,
                SalesChannel.ONLINE, "CUSTOMER_001", new BigDecimal("500000"), new BigDecimal("1000"),
                LocalDateTime.now(), LocalDateTime.now().plusYears(20), "P001", List.of(main),
                InsuranceProductType.TERM_LIFE, "BIZ_001", "PACKAGE_001", LocalDateTime.now(), "TENANT_001",
                null, null, new ChannelInfo("CHANNEL_001", "CH001", SalesChannel.ONLINE, null, null), "ANNUAL", 20);

        new ProposalProjectionEventHandler(repository, mapper).on(event);

        ArgumentCaptor<ProposalView> captor = ArgumentCaptor.forClass(ProposalView.class);
        verify(repository).save(captor.capture());
        ProposalView projected = captor.getValue();
        assertEquals("P001", projected.getExpectedProductCode());
        assertEquals(InsuranceProductType.TERM_LIFE, projected.getInsuranceType());
        assertEquals("BIZ_001", projected.getBizNo());
        assertEquals("CHANNEL_001", projected.getChannelId());
        assertEquals("PACKAGE_001", projected.getMarketPackageId());
        assertEquals(1, projected.getLineCount());
    }

    @Test
    void derivesInsuranceTypeFromMainLineWhenTopLevelTypeIsAbsent() {
        ProposalViewRepository repository = mock(ProposalViewRepository.class);
        ProposalViewMapper mapper = new ProposalViewMapperImpl();
        when(repository.findByProposalIdAndTenantId("PROPOSAL_001", "TENANT_001"))
                .thenReturn(Optional.of(new ProposalView()));
        ProposalLine main = new ProposalLine("LINE_001", 1, ProductCategory.MAIN, null, "PRODUCT_001", "P001",
                InsuranceProductType.TERM_LIFE, Money.of(new BigDecimal("500000"), "CNY"), null);
        ProposalCreatedEvent event = new ProposalCreatedEvent("PROPOSAL_001", "PRP_001", PolicyForm.INDIVIDUAL,
                SalesChannel.ONLINE, "CUSTOMER_001", new BigDecimal("500000"), null, LocalDateTime.now(),
                LocalDateTime.now().plusYears(20), "P001", List.of(main), null, "BIZ_001", null,
                LocalDateTime.now(), "TENANT_001", null, null,
                new ChannelInfo("CHANNEL_001", null, SalesChannel.ONLINE, null, null), "ANNUAL", 20);

        new ProposalProjectionEventHandler(repository, mapper).on(event);

        ArgumentCaptor<ProposalView> captor = ArgumentCaptor.forClass(ProposalView.class);
        verify(repository).save(captor.capture());
        assertEquals(InsuranceProductType.TERM_LIFE, captor.getValue().getInsuranceType());
    }

    @Test
    void infersSingleLineForHistoricalEventWithProductCode() {
        ProposalViewRepository repository = mock(ProposalViewRepository.class);
        ProposalViewMapper mapper = new ProposalViewMapperImpl();
        when(repository.findByProposalIdAndTenantId("PROPOSAL_001", "TENANT_001"))
                .thenReturn(Optional.of(new ProposalView()));
        ProposalCreatedEvent historicalEvent = new ProposalCreatedEvent("PROPOSAL_001", "PRP_001",
                PolicyForm.INDIVIDUAL, SalesChannel.ONLINE, "CUSTOMER_001", new BigDecimal("500000"),
                new BigDecimal("1000"), LocalDateTime.now(), LocalDateTime.now().plusYears(20), "P001", null,
                InsuranceProductType.TERM_LIFE, null, null, LocalDateTime.now(), "TENANT_001");

        new ProposalProjectionEventHandler(repository, mapper).on(historicalEvent);

        ArgumentCaptor<ProposalView> captor = ArgumentCaptor.forClass(ProposalView.class);
        verify(repository).save(captor.capture());
        assertEquals(1, captor.getValue().getLineCount());
    }
}
