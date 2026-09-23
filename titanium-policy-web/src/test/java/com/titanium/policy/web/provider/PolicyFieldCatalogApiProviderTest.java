package com.titanium.policy.web.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.common.context.RequestContextHolder;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.response.fieldcatalog.PolicyFieldCatalogResponse;
import com.titanium.policy.application.query.PolicyFieldCatalogApplicationService;
import com.titanium.policy.web.mapper.PolicyFieldCatalogWebMapperImpl;

class PolicyFieldCatalogApiProviderTest {

    @BeforeEach
    void setUp() {
        // 契约实现已改为从请求上下文取租户（不再由入参显式传递），故测试须先建立租户上下文
        RequestContextHolder.setTenantId("tenant-1");
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void shouldEchoQueryContextAndReturnImmutableCatalogEvidence() {
        PolicyFieldCatalogApiProvider provider = new PolicyFieldCatalogApiProvider(
                new PolicyFieldCatalogApplicationService(), new PolicyFieldCatalogWebMapperImpl());
        LocalDate businessDate = LocalDate.of(2026, 8, 24);

        ApiResponse<PolicyFieldCatalogResponse> response =
                provider.getCurrentCatalog("LIFE", "INDIVIDUAL", businessDate);

        assertTrue(response.isSuccess());
        assertEquals("tenant-1", response.getData().tenantId());
        assertEquals("LIFE", response.getData().productType());
        assertEquals("INDIVIDUAL", response.getData().policyType());
        assertEquals(businessDate, response.getData().businessDate());
        assertEquals(64, response.getData().contentHash().length());
        assertFalse(response.getData().fields().isEmpty());
        assertTrue(response.getData().fields().stream()
                .anyMatch(field -> field.fieldCode().equals("policy.holder.mobile")
                        && field.capability().proposable()
                        && field.capability().executionSupported()));
        assertTrue(response.getData().fields().stream()
                .anyMatch(field -> field.fieldCode().equals("policy.beneficiary.name")
                        && field.collection()
                        && "beneficiaryId".equals(field.objectIdentityField())
                        && field.capability().executionSupported()));
        assertTrue(response.getData().fields().stream()
                .anyMatch(field -> field.fieldCode().equals("policy.holder.email")
                        && field.capability().proposable()
                        && !field.capability().executionSupported()));
    }
}
