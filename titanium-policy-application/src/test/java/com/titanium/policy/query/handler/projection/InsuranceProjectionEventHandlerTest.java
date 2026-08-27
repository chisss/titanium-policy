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

import com.titanium.metadata.enums.billing.PremiumCollectionMode;
import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.policy.PolicyLineStatus;
import com.titanium.metadata.enums.product.ProductEnum.PaymentFrequency;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.metadata.enums.product.ProductEnum.SalesChannel;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.entity.insurance.InsuranceLine;
import com.titanium.policy.event.insurance.InsuranceCreatedEvent;
import com.titanium.policy.query.mapper.InsuranceViewMapper;
import com.titanium.policy.query.mapper.InsuranceViewMapperImpl;
import com.titanium.policy.query.repository.InsuranceViewRepository;
import com.titanium.policy.query.view.InsuranceView;
import com.titanium.policy.valueobject.policy.ChannelInfo;
import com.titanium.policy.valueobject.policy.LinePaymentTerms;

class InsuranceProjectionEventHandlerTest {

    @Test
    void projectsMainProductPaymentTermsAndLineCount() {
        InsuranceViewRepository repository = mock(InsuranceViewRepository.class);
        InsuranceViewMapper mapper = new InsuranceViewMapperImpl();
        when(repository.findByInsuranceIdAndTenantId("INSURANCE_001", "TENANT_001"))
                .thenReturn(Optional.of(new InsuranceView()));
        InsuranceLine main = new InsuranceLine("LINE_001", 1, ProductCategory.MAIN, null, "PRODUCT_001", "P001",
                "测试主险", InsuranceProductType.TERM_LIFE, Money.of(new BigDecimal("500000"), "CNY"),
                Money.of(new BigDecimal("1000"), "CNY"), null, LinePaymentTerms.annual(20), List.of(), null, null,
                PolicyLineStatus.UNDERWRITING);
        InsuranceCreatedEvent event = new InsuranceCreatedEvent("INSURANCE_001", "INS_001", "PROPOSAL_001",
                PolicyForm.INDIVIDUAL, "CUSTOMER_001", 1, new BigDecimal("1000"), LocalDateTime.now(),
                LocalDateTime.now().plusYears(20), List.of(main), 0, null, InsuranceProductType.TERM_LIFE,
                PremiumCollectionMode.ONLINE,
                new ChannelInfo("CHANNEL_001", "CH001", SalesChannel.ONLINE, null, null), "BIZ_001", "PACKAGE_001",
                LocalDateTime.now(), "TENANT_001", new BigDecimal("500000"), "ANNUAL", 20);

        new InsuranceProjectionEventHandler(repository, mapper).on(event);

        ArgumentCaptor<InsuranceView> captor = ArgumentCaptor.forClass(InsuranceView.class);
        verify(repository).save(captor.capture());
        InsuranceView projected = captor.getValue();
        assertEquals("PROPOSAL_001", projected.getProposalId());
        assertEquals("PRODUCT_001", projected.getProductId());
        assertEquals(new BigDecimal("500000"), projected.getSumInsured());
        assertEquals(PaymentFrequency.ANNUAL, projected.getPaymentFrequency());
        assertEquals(20, projected.getPremiumPaymentYears());
        assertEquals(PremiumCollectionMode.ONLINE, projected.getCollectionMode());
        assertEquals("CHANNEL_001", projected.getChannelId());
        assertEquals("BIZ_001", projected.getBizNo());
        assertEquals("PACKAGE_001", projected.getMarketPackageId());
        assertEquals(1, projected.getLineCount());
    }

    @Test
    void prefersMainLinePremiumPaymentYearsOverTopLevelCompatibilityField() {
        InsuranceViewRepository repository = mock(InsuranceViewRepository.class);
        InsuranceViewMapper mapper = new InsuranceViewMapperImpl();
        when(repository.findByInsuranceIdAndTenantId("INSURANCE_001", "TENANT_001"))
                .thenReturn(Optional.of(new InsuranceView()));
        InsuranceLine main = new InsuranceLine("LINE_001", 1, ProductCategory.MAIN, null, null, "P001", null,
                null, null, null, null, LinePaymentTerms.annual(20), List.of(), null, null,
                PolicyLineStatus.UNDERWRITING);
        InsuranceCreatedEvent event = new InsuranceCreatedEvent("INSURANCE_001", "INS_001", null,
                PolicyForm.INDIVIDUAL, "CUSTOMER_001", 1, BigDecimal.ZERO, LocalDateTime.now(),
                LocalDateTime.now().plusYears(20), List.of(main), 0, null, null, null, null, null, null,
                LocalDateTime.now(), "TENANT_001", null, "ANNUAL", 10);

        new InsuranceProjectionEventHandler(repository, mapper).on(event);

        ArgumentCaptor<InsuranceView> captor = ArgumentCaptor.forClass(InsuranceView.class);
        verify(repository).save(captor.capture());
        assertEquals(20, captor.getValue().getPremiumPaymentYears());
    }

    @Test
    void fallsBackToTopLevelPaymentTermsForHistoricalMainLineWithoutTerms() {
        InsuranceViewRepository repository = mock(InsuranceViewRepository.class);
        InsuranceViewMapper mapper = new InsuranceViewMapperImpl();
        when(repository.findByInsuranceIdAndTenantId("INSURANCE_001", "TENANT_001"))
                .thenReturn(Optional.of(new InsuranceView()));
        InsuranceLine main = new InsuranceLine("LINE_001", 1, ProductCategory.MAIN, null, null, "P001", null,
                null, null, null, null, null, List.of(), null, null, PolicyLineStatus.UNDERWRITING);
        InsuranceCreatedEvent historicalEvent = new InsuranceCreatedEvent("INSURANCE_001", "INS_001", null,
                PolicyForm.INDIVIDUAL, "CUSTOMER_001", 1, BigDecimal.ZERO, LocalDateTime.now(),
                LocalDateTime.now().plusYears(20), List.of(main), 0, null, null, null, null, null, null,
                LocalDateTime.now(), "TENANT_001", null, PaymentFrequency.ANNUAL.getCode(), 20);

        new InsuranceProjectionEventHandler(repository, mapper).on(historicalEvent);

        ArgumentCaptor<InsuranceView> captor = ArgumentCaptor.forClass(InsuranceView.class);
        verify(repository).save(captor.capture());
        assertEquals(PaymentFrequency.ANNUAL, captor.getValue().getPaymentFrequency());
        assertEquals(20, captor.getValue().getPremiumPaymentYears());
    }

    @Test
    void preservesExistingLineSnapshotWhenReplayingHistoricalEventWithoutLines() {
        InsuranceViewRepository repository = mock(InsuranceViewRepository.class);
        InsuranceViewMapper mapper = new InsuranceViewMapperImpl();
        InsuranceView existing = new InsuranceView();
        existing.setProductId("PRODUCT_001");
        existing.setPaymentFrequency(PaymentFrequency.ANNUAL);
        existing.setPremiumPaymentYears(20);
        existing.setLineCount(1);
        when(repository.findByInsuranceIdAndTenantId("INSURANCE_001", "TENANT_001"))
                .thenReturn(Optional.of(existing));
        InsuranceCreatedEvent historicalEvent = new InsuranceCreatedEvent("INSURANCE_001", "INS_001", null,
                PolicyForm.INDIVIDUAL, "CUSTOMER_001", 1, new BigDecimal("1000"), LocalDateTime.now(),
                LocalDateTime.now().plusYears(20), null, 0, null, InsuranceProductType.TERM_LIFE,
                PremiumCollectionMode.ONLINE, null, null, null, LocalDateTime.now(), "TENANT_001", null, null, 0);

        new InsuranceProjectionEventHandler(repository, mapper).on(historicalEvent);

        ArgumentCaptor<InsuranceView> captor = ArgumentCaptor.forClass(InsuranceView.class);
        verify(repository).save(captor.capture());
        InsuranceView projected = captor.getValue();
        assertEquals("PRODUCT_001", projected.getProductId());
        assertEquals(PaymentFrequency.ANNUAL, projected.getPaymentFrequency());
        assertEquals(20, projected.getPremiumPaymentYears());
        assertEquals(1, projected.getLineCount());
    }
}
