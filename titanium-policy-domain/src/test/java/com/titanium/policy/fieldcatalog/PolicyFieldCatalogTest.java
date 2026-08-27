package com.titanium.policy.fieldcatalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;

class PolicyFieldCatalogTest {

    @Test
    void shouldPublishStableStandardCatalogWithOnlyMappedExecutionCapability() {
        PolicyFieldCatalog catalog = PolicyFieldCatalog.standardV1();

        assertEquals(PolicyFieldCatalog.STANDARD_VERSION, catalog.catalogVersion());
        assertEquals(64, catalog.contentHash().length());
        assertEquals(19, catalog.fields().size());
        assertTrue(catalog.requireField("policy.holder.mobile").capability().proposable());
        assertTrue(catalog.requireField("policy.holder.mobile").capability().executionSupported());
        assertTrue(catalog.requireField("policy.coverage.sumInsured").capability().executionSupported());
        assertEquals("policyProductId",
                catalog.requireField("policy.coverage.sumInsured").objectIdentityField());
        assertFalse(catalog.requireField("policy.holder.email").capability().executionSupported());
        assertFalse(catalog.requireField("policy.status").capability().proposable());
        assertEquals(PolicyFieldMaskingPolicy.ID_NUMBER,
                catalog.requireField("policy.holder.documentNumber").maskingPolicy());
    }

    @Test
    void shouldKeepHashStableWhenInputOrderChanges() {
        PolicyFieldCatalog catalog = PolicyFieldCatalog.standardV1();
        List<PolicyFieldDescriptor> reversed = new ArrayList<>(catalog.fields());
        Collections.reverse(reversed);

        PolicyFieldCatalog restored =
                new PolicyFieldCatalog(catalog.catalogVersion(), catalog.contentHash(), reversed);

        assertEquals(catalog.contentHash(), restored.contentHash());
        assertEquals(catalog.fields(), restored.fields());
    }

    @Test
    void shouldRejectDuplicateFieldCodesAndMismatchedHash() {
        PolicyFieldCatalog catalog = PolicyFieldCatalog.standardV1();
        PolicyFieldDescriptor first = catalog.fields().getFirst();

        assertThrows(PolicyFieldCatalogValidationException.class,
                () -> new PolicyFieldCatalog("duplicate", null, List.of(first, first)));
        assertThrows(PolicyFieldCatalogValidationException.class,
                () -> new PolicyFieldCatalog(catalog.catalogVersion(), "0".repeat(64), catalog.fields()));
    }

    @Test
    void shouldRequireMaskingForSensitiveFields() {
        PolicyFieldDescriptor source = PolicyFieldCatalog.standardV1().fields().stream()
                .filter(field -> field.sensitivity() == PolicyFieldSensitivityLevel.SENSITIVE)
                .findFirst()
                .orElseThrow();

        PolicyFieldCatalogValidationException exception = assertThrows(
                PolicyFieldCatalogValidationException.class,
                () -> new PolicyFieldDescriptor(
                        "policy.test.sensitive",
                        source.objectType(),
                        source.valueType(),
                        "policy.field.test.sensitive",
                        false,
                        null,
                        source.capability(),
                        PolicyFieldSensitivityLevel.SENSITIVE,
                        PolicyFieldMaskingPolicy.NONE,
                        null));

        assertNotEquals("", exception.getErrorCode());
    }
}
