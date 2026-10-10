package com.titanium.policy.fieldcatalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldObjectType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;
import com.titanium.metadata.fieldcatalog.FieldAnalysisCapability;

/**
 * {@link PolicyFieldCatalog} 自身不变量的单元测试。
 *
 * <p>🔴 <b>本类刻意不引用任何字段清单</b>：字段清单的权威存储是目录表
 * {@code t_policy_field_catalog}（{@code G07/AC-04} 要求本域 record 内零字面量清单），
 * 「19 个字段逐项正确」由 {@code PolicyFieldCatalogAssemblerTest}（读真实种子 SQL）
 * 与 {@code validate-g07-02d.sh} 的等价迁移对账承担。</p>
 */
class PolicyFieldCatalogTest {

    private static PolicyFieldDescriptor readOnly(String code) {
        return PolicyFieldDescriptor.scalar(code, PolicyFieldObjectType.POLICY, PolicyFieldValueType.TEXT,
                "policy.field." + code, PolicyFieldCapability.readOnly(),
                PolicyFieldSensitivityLevel.INTERNAL, PolicyFieldMaskingPolicy.NONE,
                FieldAnalysisCapability.none());
    }

    @Test
    void shouldSortFieldsByCodeAndComputeSelfConsistentHash() {
        PolicyFieldCatalog catalog = new PolicyFieldCatalog("v1", null,
                List.of(readOnly("policy.zzz"), readOnly("policy.aaa"), readOnly("policy.mmm")));

        assertEquals(64, catalog.contentHash().length());
        assertEquals(List.of("policy.aaa", "policy.mmm", "policy.zzz"),
                catalog.fields().stream().map(PolicyFieldDescriptor::fieldCode).toList());
    }

    @Test
    void shouldKeepHashStableWhenInputOrderChangesAndRejectMismatchedHash() {
        PolicyFieldCatalog catalog = new PolicyFieldCatalog("v1", null,
                List.of(readOnly("policy.aaa"), readOnly("policy.bbb")));
        List<PolicyFieldDescriptor> reversed = new ArrayList<>(catalog.fields());
        Collections.reverse(reversed);

        PolicyFieldCatalog restored =
                new PolicyFieldCatalog(catalog.catalogVersion(), catalog.contentHash(), reversed);

        assertEquals(catalog.contentHash(), restored.contentHash());
        assertEquals(catalog.fields(), restored.fields());
        assertThrows(PolicyFieldCatalogValidationException.class,
                () -> new PolicyFieldCatalog(catalog.catalogVersion(), "0".repeat(64), catalog.fields()));
    }

    @Test
    void shouldRejectDuplicateFieldCodes() {
        PolicyFieldDescriptor first = readOnly("policy.aaa");

        assertThrows(PolicyFieldCatalogValidationException.class,
                () -> new PolicyFieldCatalog("v1", null, List.of(first, first)));
    }

    @Test
    void shouldRejectEmptyFieldListAndBlankVersion() {
        PolicyFieldCatalogValidationException empty =
                assertThrows(PolicyFieldCatalogValidationException.class,
                        () -> new PolicyFieldCatalog("v1", null, List.of()));
        assertEquals("字段目录不能为空", empty.getMessage());
        assertThrows(PolicyFieldCatalogValidationException.class,
                () -> new PolicyFieldCatalog("  ", null, List.of(readOnly("policy.aaa"))));
    }

    @Test
    void shouldRejectSensitiveFieldWithoutMaskingPolicy() {
        PolicyFieldCatalogValidationException exception = assertThrows(
                PolicyFieldCatalogValidationException.class,
                () -> PolicyFieldDescriptor.scalar(
                        "policy.test.sensitive",
                        PolicyFieldObjectType.POLICY,
                        PolicyFieldValueType.TEXT,
                        "policy.field.test.sensitive",
                        PolicyFieldCapability.readOnly(),
                        PolicyFieldSensitivityLevel.SENSITIVE,
                        PolicyFieldMaskingPolicy.NONE,
                        FieldAnalysisCapability.none()));

        assertNotEquals("", exception.getErrorCodeValue());
    }

    @Test
    void shouldRejectCollectionFieldWithoutIdentityField() {
        PolicyFieldCatalogValidationException exception = assertThrows(
                PolicyFieldCatalogValidationException.class,
                () -> new PolicyFieldDescriptor(
                        "policy.test.collection",
                        PolicyFieldObjectType.INSURED,
                        PolicyFieldValueType.TEXT,
                        "policy.field.test.collection",
                        true,
                        null,
                        PolicyFieldCapability.readOnly(),
                        PolicyFieldSensitivityLevel.INTERNAL,
                        PolicyFieldMaskingPolicy.NONE,
                        null,
                        FieldAnalysisCapability.none()));

        assertTrue(exception.getMessage().contains("稳定业务对象标识"));
    }
}
