package com.titanium.policy.application.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldObjectType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;
import com.titanium.policy.fieldcatalog.PolicyFieldCatalogCriteria;
import com.titanium.policy.fieldcatalog.PolicyFieldCatalogSnapshot;
import com.titanium.policy.fieldcatalog.PolicyFieldCatalogValidationException;
import com.titanium.policy.query.repository.PolicyFieldCatalogVersionViewRepository;
import com.titanium.policy.query.repository.PolicyFieldCatalogViewRepository;
import com.titanium.policy.query.view.PolicyFieldCatalogVersionView;
import com.titanium.policy.query.view.PolicyFieldCatalogView;

/**
 * 字段目录应用查询服务的测试（{@code G07/AC-05}③ 的编排面）。
 *
 * <p>锁三件事，各由一条用例承担：</p>
 * <ol>
 *   <li><b>取数按解析出的版本过滤</b>——行仓储以版本号为入参，查历史时点取到的是**当时那一版的行**，
 *       而不是最新一版（主键只按 field_code 时无历史可取）。</li>
 *   <li><b>降级标记贯通到快照</b>——早于最早版本时不报错，但 {@code fallback=true} 必须落到快照。</li>
 *   <li><b>解析不出结果时不得读行</b>——空表与时间空档都在读行之前失败（fail-closed 且不浪费取数）。</li>
 * </ol>
 */
class PolicyFieldCatalogApplicationServiceTest {

    private static final String V1 = "2026.09.14.2";
    private static final String V2 = "2026.10.01.1";
    private static final LocalDate SEPT_14 = LocalDate.of(2026, 9, 14);
    private static final LocalDate OCT_01 = LocalDate.of(2026, 10, 1);

    private final PolicyFieldCatalogVersionViewRepository versionRepository =
            mock(PolicyFieldCatalogVersionViewRepository.class);
    private final PolicyFieldCatalogViewRepository viewRepository =
            mock(PolicyFieldCatalogViewRepository.class);
    private final PolicyFieldCatalogAssembler assembler = new PolicyFieldCatalogAssembler();
    private final PolicyFieldCatalogApplicationService service =
            new PolicyFieldCatalogApplicationService(versionRepository, viewRepository, assembler);

    @Test
    @DisplayName("AC-05③ 取数按业务日期解析出的版本过滤，历史时点取到的是当时那一版的行")
    void shouldLoadRowsOfVersionResolvedByBusinessDate() {
        List<PolicyFieldCatalogView> versionOneRows = rows(V1, "policy.status", "policy.holder.mobile");
        List<PolicyFieldCatalogView> versionTwoRows =
                rows(V2, "policy.status", "policy.holder.mobile", "policy.beneficiary.name");
        givenPublished(
                versionView(V1, hash(V1, versionOneRows), SEPT_14, OCT_01),
                versionView(V2, hash(V2, versionTwoRows), OCT_01, null));
        when(viewRepository.findByCatalogVersionOrderByFieldCodeAsc(V1)).thenReturn(versionOneRows);
        when(viewRepository.findByCatalogVersionOrderByFieldCodeAsc(V2)).thenReturn(versionTwoRows);

        PolicyFieldCatalogSnapshot historical = service.getCatalogAt(criteria(LocalDate.of(2026, 9, 20)));
        PolicyFieldCatalogSnapshot current = service.getCatalogAt(criteria(LocalDate.of(2026, 10, 15)));

        assertEquals(V1, historical.catalogVersion(), "09-20 应命中前一版");
        assertEquals(2, historical.catalog().fields().size(), "取的是 v1 的行集，不是最新的 v2");
        assertFalse(historical.fallback());
        assertEquals(V2, current.catalogVersion(), "10-15 应命中后一版");
        assertEquals(3, current.catalog().fields().size());
        assertFalse(current.fallback());
    }

    @Test
    @DisplayName("AC-05③ 早于最早版本 ⇒ 不报错，但快照必须带 fallback=true")
    void shouldPropagateFallbackFlagIntoSnapshot() {
        List<PolicyFieldCatalogView> versionOneRows = rows(V1, "policy.status");
        givenPublished(versionView(V1, hash(V1, versionOneRows), SEPT_14, OCT_01));
        when(viewRepository.findByCatalogVersionOrderByFieldCodeAsc(V1)).thenReturn(versionOneRows);

        PolicyFieldCatalogSnapshot snapshot = service.getCatalogAt(criteria(LocalDate.of(2020, 1, 1)));

        assertTrue(snapshot.fallback(), "降级必须显式可见，不得静默");
        assertEquals(V1, snapshot.catalogVersion(), "降级取最早版本");
        assertEquals(LocalDate.of(2020, 1, 1), snapshot.businessDate(), "业务日期原样带回");
    }

    @Test
    @DisplayName("AC-05③ 反例：时间空档 ⇒ 拒绝，且不发生行读取")
    void shouldRejectBusinessDateInGapWithoutReadingRows() {
        givenPublished(
                versionView(V1, "a".repeat(64), SEPT_14, LocalDate.of(2026, 9, 20)),
                versionView(V2, "b".repeat(64), OCT_01, null));

        PolicyFieldCatalogValidationException exception = assertThrows(
                PolicyFieldCatalogValidationException.class,
                () -> service.getCatalogAt(criteria(LocalDate.of(2026, 9, 25))));

        assertTrue(exception.getMessage().contains("空档"));
        verifyNoInteractions(viewRepository);
    }

    @Test
    @DisplayName("反例：版本表为空 ⇒ 拒绝，且不发生行读取")
    void shouldRejectEmptyVersionTableWithoutReadingRows() {
        givenPublished();

        assertTrue(assertThrows(PolicyFieldCatalogValidationException.class,
                () -> service.getCatalogAt(criteria(SEPT_14))).getMessage().contains("未初始化"));
        verifyNoInteractions(viewRepository);
    }

    @Test
    @DisplayName("🔴 读取时完整性：行内容与版本头冻结哈希不符 ⇒ 拒绝（内容被改而版本没升）")
    void shouldFailClosedWhenRowsDoNotMatchFrozenHash() {
        List<PolicyFieldCatalogView> rows = rows(V1, "policy.status", "policy.holder.mobile");
        // 版本头冻结的是「三个字段」那版内容，行表却只剩两行 ⇒ AC-05② 禁止的动作在读取时被拦下
        givenPublished(versionView(V1, hash(V1, rows(V1, "policy.status", "policy.holder.mobile",
                "policy.beneficiary.name")), SEPT_14, null));
        when(viewRepository.findByCatalogVersionOrderByFieldCodeAsc(V1)).thenReturn(rows);

        assertTrue(assertThrows(PolicyFieldCatalogValidationException.class,
                () -> service.getCatalogAt(criteria(SEPT_14))).getMessage().contains("哈希"));
    }

    @Test
    @DisplayName("反例：查询条件为空 ⇒ 拒绝")
    void shouldRejectNullCriteria() {
        assertThrows(PolicyFieldCatalogValidationException.class, () -> service.getCatalogAt(null));
        verifyNoInteractions(versionRepository, viewRepository);
    }

    // ------------------------------------------------------------ 夹具

    private void givenPublished(PolicyFieldCatalogVersionView... views) {
        when(versionRepository.findAllByOrderByEffectiveFromAscCatalogVersionAsc())
                .thenReturn(List.of(views));
    }

    private static PolicyFieldCatalogCriteria criteria(LocalDate businessDate) {
        return new PolicyFieldCatalogCriteria("tenant-1", "LIFE", "INDIVIDUAL", businessDate);
    }

    /** 行集的内容哈希由装配器的计算路径得出——见 {@code PolicyFieldCatalogAssemblerTest} 类注释关于夹具哈希的说明。 */
    private String hash(String catalogVersion, List<PolicyFieldCatalogView> rows) {
        return new PolicyFieldCatalogAssembler().computeContent(catalogVersion, rows).contentHash();
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

    private static List<PolicyFieldCatalogView> rows(String catalogVersion, String... fieldCodes) {
        return Arrays.stream(fieldCodes)
                .map(fieldCode -> row(catalogVersion, fieldCode))
                .toList();
    }

    private static PolicyFieldCatalogView row(String catalogVersion, String fieldCode) {
        PolicyFieldCatalogView view = new PolicyFieldCatalogView();
        view.setCatalogVersion(catalogVersion);
        view.setFieldCode(fieldCode);
        view.setObjectType(PolicyFieldObjectType.POLICY);
        view.setValueType(PolicyFieldValueType.TEXT);
        view.setLabelKey("policy.field." + fieldCode);
        view.setSensitivity(PolicyFieldSensitivityLevel.INTERNAL);
        view.setMaskingPolicy(PolicyFieldMaskingPolicy.NONE);
        view.setAnalyzable(true);
        view.setAggregatable(false);
        view.setGroupable(false);
        view.setFilterable(true);
        return view;
    }
}
