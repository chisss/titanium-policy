package com.titanium.policy.application.orchestration.issuance.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.metadata.enums.billing.PremiumCollectionMode;
import com.titanium.metadata.enums.customer.CustomerEnum.CustomerGender;
import com.titanium.metadata.enums.customer.CustomerEnum.IdCardType;
import com.titanium.metadata.enums.policy.BeneficiaryType;
import com.titanium.metadata.enums.policy.IssuanceStrategy;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.policy.application.exception.CustomerResolutionException;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.port.CustomerServicePort;
import com.titanium.policy.valueobject.IssuancePlanLine;
import com.titanium.policy.valueobject.IssuanceRequest;
import com.titanium.policy.valueobject.customer.CustomerIdentitySnapshot;

class IssuanceCustomerResolverTest {

    private static final String TENANT_ID = "TENANT_001";
    private static final String HOLDER_ID = "CUSTOMER_HOLDER";
    private CustomerServicePort customerServicePort;
    private IssuanceCustomerResolver resolver;

    @BeforeEach
    void setUp() {
        customerServicePort = mock(CustomerServicePort.class);
        resolver = new IssuanceCustomerResolver(customerServicePort);
    }

    @Test
    void explicitCustomerIdMustBelongToRequestTenant() {
        when(customerServicePort.findCustomerIdentity(HOLDER_ID, TENANT_ID)).thenReturn(Optional.empty());

        CustomerResolutionException exception = assertThrows(CustomerResolutionException.class,
                () -> resolver.resolve(request(new InsuredPartyList("PARTIES_001", holder(HOLDER_ID),
                        List.of(), List.of()))));

        assertEquals(PolicyErrorCode.ISSUANCE_CUSTOMER_NOT_FOUND.getCode(), exception.errorCode());
        verify(customerServicePort, never()).resolveCustomer(any(), eq(TENANT_ID));
    }

    @Test
    void explicitCustomerIdMustMatchProvidedIdentitySnapshot() {
        when(customerServicePort.findCustomerIdentity(HOLDER_ID, TENANT_ID)).thenReturn(Optional.of(
                identity("李四", IdCardType.CHINA_ID_CARD, "110101199001011234", null, null)));

        CustomerResolutionException exception = assertThrows(CustomerResolutionException.class,
                () -> resolver.resolve(request(new InsuredPartyList("PARTIES_001", holder(HOLDER_ID),
                        List.of(), List.of()))));

        assertEquals(PolicyErrorCode.ISSUANCE_CUSTOMER_IDENTITY_MISMATCH.getCode(), exception.errorCode());
        assertFalse(exception.retryable());
    }

    @Test
    void sameNaturalIdentityIsResolvedOnceAndReusedForInsuredParty() {
        when(customerServicePort.resolveCustomer(any(), eq(TENANT_ID))).thenReturn(HOLDER_ID);

        InsuredPartyList parties = new InsuredPartyList("PARTIES_001",
                holder(null),
                List.of(new InsuredPartyList.InsuredInfo(null, "INSURED_001", "张三", IdCardType.CHINA_ID_CARD,
                        " 110101199001011234 ", 35, null, "SELF", null)),
                List.of());
        IssuanceRequest resolved = resolver.resolve(request(parties));

        assertEquals(HOLDER_ID, resolved.holderCustomerId());
        assertEquals(HOLDER_ID, resolved.insuredPartyList().insuredList().get(0).customerId());
        assertEquals(null, resolved.insuredPartyList().insuredList().get(0).phone());
        assertEquals("SELF", resolved.insuredPartyList().insuredList().get(0).relationToHolder());
        verify(customerServicePort).resolveCustomer(any(), eq(TENANT_ID));
    }

    @Test
    void selfInsuredGenderIsUsedWhenCreatingSharedHolderCustomer() {
        when(customerServicePort.resolveCustomer(any(), eq(TENANT_ID))).thenReturn(HOLDER_ID);

        InsuredPartyList parties = new InsuredPartyList("PARTIES_002", holder(null),
                List.of(new InsuredPartyList.InsuredInfo(null, "INSURED_002", "张三", IdCardType.CHINA_ID_CARD,
                        "110101199001011234", 35, CustomerGender.MALE, "13800138001", "SELF", null)), List.of());
        resolver.resolve(request(parties));

        ArgumentCaptor<com.titanium.policy.valueobject.customer.CustomerIdentitySnapshot> captor =
                ArgumentCaptor.forClass(com.titanium.policy.valueobject.customer.CustomerIdentitySnapshot.class);
        verify(customerServicePort).resolveCustomer(captor.capture(), eq(TENANT_ID));
        assertEquals(CustomerGender.MALE, captor.getValue().gender());
        assertEquals("13800138000", captor.getValue().phoneNumber());
    }

    @Test
    void selfRelationMustReuseHolderCustomerEvenWhenInsuredIdIsMissing() {
        when(customerServicePort.findCustomerIdentity(HOLDER_ID, TENANT_ID)).thenReturn(Optional.of(
                identity("张三", IdCardType.CHINA_ID_CARD, "110101199001011234", CustomerGender.MALE,
                        "13800138000")));
        InsuredPartyList parties = new InsuredPartyList("PARTIES_003", holder(HOLDER_ID),
                List.of(new InsuredPartyList.InsuredInfo(null, "INSURED_003", "张三", IdCardType.CHINA_ID_CARD,
                        "110101199001011234", 35, CustomerGender.MALE, "13800138000", "SELF", null)), List.of());

        IssuanceRequest resolved = resolver.resolve(request(parties));

        assertEquals(HOLDER_ID, resolved.insuredPartyList().insuredList().get(0).customerId());
        verify(customerServicePort, never()).resolveCustomer(any(), eq(TENANT_ID));
    }

    @Test
    void selfRelationRejectsDifferentExplicitCustomerId() {
        when(customerServicePort.findCustomerIdentity(HOLDER_ID, TENANT_ID)).thenReturn(Optional.of(
                identity("张三", IdCardType.CHINA_ID_CARD, "110101199001011234", null, null)));
        InsuredPartyList parties = new InsuredPartyList("PARTIES_004", holder(HOLDER_ID),
                List.of(new InsuredPartyList.InsuredInfo("OTHER_CUSTOMER", "INSURED_004", "张三",
                        IdCardType.CHINA_ID_CARD, "110101199001011234", 35, null, "SELF", null)), List.of());

        CustomerResolutionException exception = assertThrows(CustomerResolutionException.class,
                () -> resolver.resolve(request(parties)));

        assertEquals(PolicyErrorCode.ISSUANCE_CUSTOMER_IDENTITY_MISMATCH.getCode(), exception.errorCode());
    }

    @Test
    void beneficiaryGenderAndPhoneAreUsedForResolutionAndPreserved() {
        when(customerServicePort.resolveCustomer(any(), eq(TENANT_ID)))
                .thenReturn(HOLDER_ID, "CUSTOMER_BENEFICIARY");
        InsuredPartyList.BeneficiaryInfo beneficiary = new InsuredPartyList.BeneficiaryInfo(null, "BENEFICIARY_001",
                "李四", IdCardType.CHINA_ID_CARD, "110101199101011235", CustomerGender.FEMALE,
                "13900139000", BeneficiaryType.DEATH, 1, 1.0d);
        InsuredPartyList parties = new InsuredPartyList("PARTIES_005", holder(null), List.of(), List.of(beneficiary));

        IssuanceRequest resolved = resolver.resolve(request(parties));

        ArgumentCaptor<CustomerIdentitySnapshot> captor = ArgumentCaptor.forClass(CustomerIdentitySnapshot.class);
        verify(customerServicePort, times(2)).resolveCustomer(captor.capture(), eq(TENANT_ID));
        CustomerIdentitySnapshot beneficiaryIdentity = captor.getAllValues().get(1);
        assertEquals(CustomerGender.FEMALE, beneficiaryIdentity.gender());
        assertEquals("13900139000", beneficiaryIdentity.phoneNumber());
        assertEquals(CustomerGender.FEMALE, resolved.insuredPartyList().beneficiaryList().get(0).gender());
        assertEquals("13900139000", resolved.insuredPartyList().beneficiaryList().get(0).phone());
    }

    @Test
    void customerServiceFailureIsMarkedRetryable() {
        when(customerServicePort.findCustomerIdentity(HOLDER_ID, TENANT_ID))
                .thenThrow(new IllegalStateException("customer service unavailable"));

        CustomerResolutionException exception = assertThrows(CustomerResolutionException.class,
                () -> resolver.resolve(request(new InsuredPartyList("PARTIES_006", holder(HOLDER_ID),
                        List.of(), List.of()))));

        assertEquals(PolicyErrorCode.ISSUANCE_CUSTOMER_RESOLUTION_FAILED.getCode(), exception.errorCode());
        assertTrue(exception.retryable());
    }

    private IssuanceRequest request(InsuredPartyList parties) {
        IssuancePlanLine line = new IssuancePlanLine(1, "PRODUCT_001", ProductCategory.MAIN, null, null, null, null,
                null, null, List.of(), null);
        return new IssuanceRequest("BIZ_001", TENANT_ID, "USER_001", null, IssuanceStrategy.MERGE_ONE_POLICY,
                parties.holderInfo() != null ? parties.holderInfo().customerId() : null, parties, PolicyForm.INDIVIDUAL,
                null, LocalDateTime.now(), LocalDateTime.now().plusYears(1), PremiumCollectionMode.ONLINE, null, null,
                null, List.of(line), null, null);
    }

    private InsuredPartyList.HolderInfo holder(String customerId) {
        return new InsuredPartyList.HolderInfo(customerId, "HOLDER_001", "张三", IdCardType.CHINA_ID_CARD,
                "110101199001011234", "13800138000");
    }

    private CustomerIdentitySnapshot identity(String name, IdCardType idType, String idNo, CustomerGender gender,
                                              String phone) {
        return new CustomerIdentitySnapshot(name, idType, idNo, gender, phone, null);
    }
}
