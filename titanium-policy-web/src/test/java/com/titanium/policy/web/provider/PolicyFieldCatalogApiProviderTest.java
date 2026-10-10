package com.titanium.policy.web.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.common.context.RequestContextHolder;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldObjectType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.response.fieldcatalog.PolicyFieldCatalogResponse;
import com.titanium.policy.application.query.PolicyFieldCatalogApplicationService;
import com.titanium.policy.application.query.PolicyFieldCatalogAssembler;
import com.titanium.policy.query.repository.PolicyFieldCatalogVersionViewRepository;
import com.titanium.policy.query.repository.PolicyFieldCatalogViewRepository;
import com.titanium.policy.query.view.PolicyFieldCatalogVersionView;
import com.titanium.policy.query.view.PolicyFieldCatalogView;
import com.titanium.policy.web.mapper.PolicyFieldCatalogWebMapperImpl;

/**
 * 字段目录 Feign 契约实现（provider）的测试。
 *
 * <p>本类只锁<strong>契约层</strong>行为——租户与查询上下文的回显、响应形状（含版本头与降级标记）、
 * 能力位不丢。「19 个字段逐项正确、与迁移前哈希一致」由
 * {@code PolicyFieldCatalogAssemblerTest}（读真实种子 SQL）承担；「业务时点 → 版本归属」由
 * {@code PolicyFieldCatalogApplicationServiceTest} 承担，本类不重复。</p>
 */
class PolicyFieldCatalogApiProviderTest {

    private static final String VERSION = "2026.09.14.2";

    /** 版本生效起始日（与版本号日期段同口径）。 */
    private static final LocalDate EFFECTIVE_FROM = LocalDate.of(2026, 9, 14);

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
        List<PolicyFieldCatalogView> rows = List.of(
                row("policy.holder.mobile", false, null,
                        true, true, true, true, false, "POLICY_INFO_CHANGE",
                        PolicyFieldSensitivityLevel.SENSITIVE, PolicyFieldMaskingPolicy.MOBILE),
                row("policy.holder.email", false, null,
                        true, true, true, false, false, "POLICY_INFO_CHANGE",
                        PolicyFieldSensitivityLevel.SENSITIVE, PolicyFieldMaskingPolicy.EMAIL),
                row("policy.beneficiary.name", true, "beneficiaryId",
                        true, true, true, true, true, "BENEFICIARY_CHANGE",
                        PolicyFieldSensitivityLevel.SENSITIVE, PolicyFieldMaskingPolicy.NAME));
        PolicyFieldCatalogApiProvider provider = provider(rows);

        LocalDate businessDate = LocalDate.of(2026, 9, 20);
        ApiResponse<PolicyFieldCatalogResponse> response =
                provider.getCurrentCatalog("LIFE", "INDIVIDUAL", businessDate);

        assertTrue(response.isSuccess());
        assertEquals("tenant-1", response.getData().tenantId());
        assertEquals("LIFE", response.getData().productType());
        assertEquals("INDIVIDUAL", response.getData().policyType());
        assertEquals(businessDate, response.getData().businessDate());
        assertFalse(response.getData().fallback(), "业务日期落在生效区间内 ⇒ 不得标记降级");
        assertEquals(VERSION, response.getData().catalogVersion(), "响应必须回带命中的目录版本");
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

    /**
     * 🔴 降级标记必须**透出到跨域契约**（{@code G07/g07-04}）：业务日期早于最早版本时，
     * 上游返回最早版本并标记 {@code fallback=true}（不报错）；消费方（保全域 Adapter）
     * 据此记 warn。响应里抹掉该标记，等于把「内容不是该时点的」这一事实隐藏。
     */
    @Test
    void shouldExposeFallbackFlagWhenBusinessDatePrecedesEarliestVersion() {
        PolicyFieldCatalogApiProvider provider = provider(List.of(
                row("policy.holder.mobile", false, null,
                        true, true, true, true, false, "POLICY_INFO_CHANGE",
                        PolicyFieldSensitivityLevel.SENSITIVE, PolicyFieldMaskingPolicy.MOBILE)));

        ApiResponse<PolicyFieldCatalogResponse> response =
                provider.getCurrentCatalog("LIFE", "INDIVIDUAL", EFFECTIVE_FROM.minusDays(30));

        assertTrue(response.isSuccess(), "降级是有意为之的返回，不是失败");
        assertTrue(response.getData().fallback(), "业务日期早于最早版本 ⇒ 必须标记降级");
        assertEquals(VERSION, response.getData().catalogVersion(), "降级时返回的是最早版本");
        assertFalse(response.getData().fields().isEmpty(), "降级仍返回完整目录内容");
    }

    private static PolicyFieldCatalogApiProvider provider(List<PolicyFieldCatalogView> rows) {
        PolicyFieldCatalogViewRepository viewRepository = mock(PolicyFieldCatalogViewRepository.class);
        PolicyFieldCatalogVersionViewRepository versionRepository =
                mock(PolicyFieldCatalogVersionViewRepository.class);
        String contentHash = new PolicyFieldCatalogAssembler().computeContent(VERSION, rows).contentHash();
        when(versionRepository.findAllByOrderByEffectiveFromAscCatalogVersionAsc())
                .thenReturn(List.of(versionView(VERSION, contentHash, EFFECTIVE_FROM, null)));
        when(viewRepository.findByCatalogVersionOrderByFieldCodeAsc(VERSION)).thenReturn(rows);
        return new PolicyFieldCatalogApiProvider(
                new PolicyFieldCatalogApplicationService(
                        versionRepository, viewRepository, new PolicyFieldCatalogAssembler()),
                new PolicyFieldCatalogWebMapperImpl());
    }

    private static PolicyFieldCatalogVersionView versionView(
            String catalogVersion, String contentHash, LocalDate effectiveFrom, LocalDate effectiveTo) {
        PolicyFieldCatalogVersionView view = new PolicyFieldCatalogVersionView();
        view.setCatalogVersion(catalogVersion);
        view.setContentHash(contentHash);
        view.setEffectiveFrom(effectiveFrom);
        view.setEffectiveTo(effectiveTo);
        view.setPublishedBy("system");
        view.setPublishedAt(LocalDateTime.of(2026, 9, 14, 10, 0));
        return view;
    }

    private static PolicyFieldCatalogView row(String fieldCode, boolean collection, String objectIdentityField,
            boolean readable, boolean proposable, boolean clearable, boolean executionSupported,
            boolean requiresObjectId, String changeTypeCode,
            PolicyFieldSensitivityLevel sensitivity, PolicyFieldMaskingPolicy maskingPolicy) {
        PolicyFieldCatalogView view = new PolicyFieldCatalogView();
        view.setCatalogVersion(VERSION);
        view.setFieldCode(fieldCode);
        view.setObjectType(PolicyFieldObjectType.POLICY);
        view.setValueType(PolicyFieldValueType.TEXT);
        view.setLabelKey("policy.field." + fieldCode);
        view.setCollection(collection);
        view.setObjectIdentityField(objectIdentityField);
        view.setReadable(readable);
        view.setProposable(proposable);
        view.setClearable(clearable);
        view.setExecutionSupported(executionSupported);
        view.setRequiresObjectId(requiresObjectId);
        view.setChangeTypeCode(changeTypeCode);
        view.setSensitivity(sensitivity);
        view.setMaskingPolicy(maskingPolicy);
        view.setAnalyzable(true);
        view.setAggregatable(false);
        view.setGroupable(collection);
        view.setFilterable(true);
        return view;
    }
}
