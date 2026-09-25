package com.titanium.policy.infrastructure.adapter.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.titanium.customer.api.CustomerApi;
import com.titanium.customer.api.response.CustomerResponse;
import com.titanium.metadata.enums.customer.CustomerEnum.CustomerGender;
import com.titanium.metadata.enums.customer.CustomerEnum.IdCardType;
import com.titanium.metadata.errorcode.SystemErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.valueobject.customer.CustomerIdentitySnapshot;

class CustomerServiceAdapterTest {

    @Test
    void mapsExistingCustomerDetailToIdentitySnapshot() {
        CustomerApi customerApi = mock(CustomerApi.class);
        CustomerResponse response = CustomerResponse.builder()
                .customerId("CUSTOMER_001")
                .fullName("张三")
                .idType(IdCardType.CHINA_ID_CARD)
                .idNo("110101199001011234")
                .gender(CustomerGender.MALE)
                .phoneNumber("13800138000")
                .build();
        when(customerApi.getCustomer("CUSTOMER_001")).thenReturn(ApiResponse.success(response));

        Optional<CustomerIdentitySnapshot> identity = new CustomerServiceAdapter(customerApi)
                .findCustomerIdentity("CUSTOMER_001", "TENANT_001");

        assertTrue(identity.isPresent());
        assertEquals("张三", identity.get().fullName());
        assertEquals(IdCardType.CHINA_ID_CARD, identity.get().idType());
        assertEquals("110101199001011234", identity.get().idNo());
        assertEquals(CustomerGender.MALE, identity.get().gender());
        assertEquals("13800138000", identity.get().phoneNumber());
    }

    @Test
    void returnsEmptyWhenCustomerDoesNotExist() {
        CustomerApi customerApi = mock(CustomerApi.class);
        // m24-01a 信封化：对端契约「不存在」的形态是 2xx + 成功信封 + data 为 null
        when(customerApi.getCustomer("MISSING")).thenReturn(ApiResponse.success(null));

        Optional<CustomerIdentitySnapshot> identity = new CustomerServiceAdapter(customerApi)
                .findCustomerIdentity("MISSING", "TENANT_001");

        assertTrue(identity.isEmpty());
    }

    @Test
    void propagatesErrorEnvelopeInsteadOfReportingMissingCustomer() {
        CustomerApi customerApi = mock(CustomerApi.class);
        when(customerApi.getCustomer("CUSTOMER_001"))
                .thenReturn(ApiResponse.error(SystemErrorCode.DEPENDENCY_SERVICE_ERROR));

        // 🔴 错误信封（2xx + 错误码）不得并入「客户不存在」：契约失败会被读成输入问题
        assertThrows(IllegalStateException.class, () -> new CustomerServiceAdapter(customerApi)
                .findCustomerIdentity("CUSTOMER_001", "TENANT_001"));
    }
}
