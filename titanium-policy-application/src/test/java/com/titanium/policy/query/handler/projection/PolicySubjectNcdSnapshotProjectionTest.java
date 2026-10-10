package com.titanium.policy.query.handler.projection;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.metadata.enums.insurance.SubjectType;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.common.constant.PolicyConstants;
import com.titanium.policy.entity.policy.InsuredSubject;
import com.titanium.policy.entity.policy.PolicyProduct;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.query.repository.PolicyClauseViewRepository;
import com.titanium.policy.query.repository.PolicyCoverageViewRepository;
import com.titanium.policy.query.repository.PolicyProductViewRepository;
import com.titanium.policy.query.repository.PolicySubjectViewRepository;
import com.titanium.policy.query.view.PolicySubjectView;
import com.titanium.policy.valueobject.policy.LineCoveragePeriod;

/**
 * NCD 反算取证落库投影测试（G11/g11-03 AC-04）
 * <p>
 * 断言标的属性包中的 NCD 取证键被提升为 {@code t_policy_subject} 的六列（系数/条数/区间起止/
 * 规则编码/规则版本）。取数侧（product 域反算）与契约侧（claim 历史赔案 Port）由
 * {@code NcdResolutionTest} 覆盖，本类只管**落库与可见**这一段。
 * </p>
 * <p>
 * 🔴 本类刻意把两条反向判据立成用例：① 取证键缺失时六列**必须为 null**（尤其系数不得落 1.0、
 * 条数不得落 0）——落 1.0 会让「未反算/取不到历史」与「确无赔款」在库中不可区分（AC-03 禁止）；
 * ② 重投影（批改/换单）后**不得残留上一版取证值**——列上留着旧数字比留空更坏，它看起来像本次反算的。
 * </p>
 */
class PolicySubjectNcdSnapshotProjectionTest {

    private static final String POLICY_ID = "POLICY_001";
    private static final String TENANT_ID = "TENANT_001";
    private static final String SUBJECT_ID = "SUBJECT_001";
    private static final LocalDateTime PERIOD_START = LocalDateTime.of(2026, 9, 1, 0, 0);
    private static final LocalDateTime PERIOD_END = LocalDateTime.of(2027, 9, 1, 0, 0);
    private static final LocalDateTime NCD_FROM = LocalDateTime.of(2025, 10, 10, 0, 0);
    private static final LocalDateTime NCD_TO = LocalDateTime.of(2026, 10, 11, 0, 0);

    /** AC-04 正例：取证键齐备（区间为 ISO-8601 字符串形态）→ 六列逐一落值。 */
    @Test
    void projectsNcdEvidenceFromSubjectAttributes() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(PolicyConstants.SubjectAttributeKey.NCD, "0.85000000");
        attributes.put(PolicyConstants.SubjectAttributeKey.NCD_CLAIM_COUNT, "2");
        attributes.put(PolicyConstants.SubjectAttributeKey.NCD_PERIOD_FROM, "2025-10-10T00:00");
        attributes.put(PolicyConstants.SubjectAttributeKey.NCD_PERIOD_TO, "2026-10-11T00:00");
        attributes.put(PolicyConstants.SubjectAttributeKey.NCD_RULE_CODE, "NCD-AUTO");
        attributes.put(PolicyConstants.SubjectAttributeKey.NCD_RULE_VERSION, "V1.0");

        PolicySubjectView view = project(attributes);

        assertEquals(0, new BigDecimal("0.85").compareTo(view.getNcdCoefficient()));
        assertEquals(2, view.getNcdClaimCount());
        assertEquals(NCD_FROM, view.getNcdPeriodFrom());
        assertEquals(NCD_TO, view.getNcdPeriodTo());
        assertEquals("NCD-AUTO", view.getNcdRuleCode());
        assertEquals("V1.0", view.getNcdRuleVersion());
    }

    /** 时间值的实际类型取决于写入侧构造形态，日期对象与日期串两种形态都要能落列。 */
    @Test
    void parsesDateTypedAndDateOnlyEvidenceValues() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(PolicyConstants.SubjectAttributeKey.NCD, new BigDecimal("0.70"));
        attributes.put(PolicyConstants.SubjectAttributeKey.NCD_CLAIM_COUNT, 0);
        attributes.put(PolicyConstants.SubjectAttributeKey.NCD_PERIOD_FROM, LocalDate.of(2025, 10, 10));
        attributes.put(PolicyConstants.SubjectAttributeKey.NCD_PERIOD_TO, NCD_TO);

        PolicySubjectView view = project(attributes);

        assertEquals(NCD_FROM, view.getNcdPeriodFrom(), "日期型取证值取当日零点");
        assertEquals(NCD_TO, view.getNcdPeriodTo(), "日期时间型取证值原样落列");
        assertEquals(0, view.getNcdClaimCount(), "0 次是「确无赔款」的合法取证值，须与 null 区分");
    }

    /**
     * 🔴 AC-03 反向判据：取证键缺失 → 六列全 null。
     * <p>
     * 系数落 1.0、条数落 0 都会把「未反算 / 取不到历史」粉饰成「确无赔款」，故逐一断言为 {@code null}。
     * </p>
     */
    @Test
    void leavesAllColumnsNullWhenEvidenceAbsent() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("vin", "LVIN0000000000001");
        attributes.put("licensePlate", "沪A12345");

        PolicySubjectView view = project(attributes);

        assertNull(view.getNcdCoefficient(), "未反算不得以 1.0 代填");
        assertNull(view.getNcdClaimCount(), "未反算不得以 0 代填（0 是「确无赔款」的证据）");
        assertNull(view.getNcdPeriodFrom());
        assertNull(view.getNcdPeriodTo());
        assertNull(view.getNcdRuleCode());
        assertNull(view.getNcdRuleVersion());
    }

    /** 🔴 重投影不得残留上一版取证值：库中旧行带值，本次属性包无取证键 ⇒ 六列必须被清空。 */
    @Test
    void clearsStaleEvidenceOnReprojection() {
        PolicySubjectView existing = new PolicySubjectView();
        existing.setNcdCoefficient(new BigDecimal("1.25000000"));
        existing.setNcdClaimCount(9);
        existing.setNcdPeriodFrom(LocalDateTime.of(2020, 1, 1, 0, 0));
        existing.setNcdPeriodTo(LocalDateTime.of(2021, 1, 1, 0, 0));
        existing.setNcdRuleCode("NCD-OLD");
        existing.setNcdRuleVersion("V0.9");

        PolicySubjectView view = project(Map.of("vin", "LVIN0000000000001"), existing);

        assertNull(view.getNcdCoefficient(), "旧版取证值不得随行残留");
        assertNull(view.getNcdClaimCount());
        assertNull(view.getNcdPeriodFrom());
        assertNull(view.getNcdPeriodTo());
        assertNull(view.getNcdRuleCode());
        assertNull(view.getNcdRuleVersion());
    }

    /** 区间值不可解析时只置该列为空，**不得抛出**——抛出会让整批投影事务回滚、其余标的连带丢失。 */
    @Test
    void toleratesUnparsablePeriodValue() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(PolicyConstants.SubjectAttributeKey.NCD, "0.95");
        attributes.put(PolicyConstants.SubjectAttributeKey.NCD_PERIOD_FROM, "2025年10月10日");
        attributes.put(PolicyConstants.SubjectAttributeKey.NCD_RULE_CODE, "NCD-AUTO");

        PolicySubjectView view = assertDoesNotThrow(() -> project(attributes));

        assertNull(view.getNcdPeriodFrom(), "不可解析即视为未取证，不做猜测");
        assertEquals(0, new BigDecimal("0.95").compareTo(view.getNcdCoefficient()), "其余取证列不受影响");
        assertEquals("NCD-AUTO", view.getNcdRuleCode());
    }

    // ---------- 测试夹具 ----------

    private PolicySubjectView project(Map<String, Object> attributes) {
        return project(attributes, null);
    }

    private PolicySubjectView project(Map<String, Object> attributes, PolicySubjectView existing) {
        PolicyProductViewRepository productRepository = mock(PolicyProductViewRepository.class);
        when(productRepository.findById(anyString())).thenReturn(Optional.empty());
        PolicySubjectViewRepository subjectRepository = mock(PolicySubjectViewRepository.class);
        when(subjectRepository.findById(anyString())).thenReturn(Optional.ofNullable(existing));

        new PolicyLineProjectionEventHandler(productRepository, mock(PolicyClauseViewRepository.class),
                subjectRepository, mock(PolicyCoverageViewRepository.class)).on(eventWithVehicleSubject(attributes));

        ArgumentCaptor<PolicySubjectView> captor = ArgumentCaptor.forClass(PolicySubjectView.class);
        verify(subjectRepository).save(captor.capture());
        return captor.getValue();
    }

    private PolicyCreatedEvent eventWithVehicleSubject(Map<String, Object> attributes) {
        InsuredSubject subject = InsuredSubject.ofObject(SUBJECT_ID, SubjectType.VEHICLE, "沪A12345",
                Money.of(new BigDecimal("200000.00"), "CNY"), attributes);
        PolicyProduct line = new PolicyProduct("LINE_1", 1, ProductCategory.MAIN, null, "PRODUCT_1", "AUTO-CMR",
                "机动车综合商业保险", "V1.0", "PLAN_V1", null, null, null,
                LineCoveragePeriod.fixedTerm(PERIOD_START, PERIOD_END, null, null), null, null, null,
                List.of(), List.of(subject), List.of(), null);
        return new PolicyCreatedEvent(POLICY_ID, null, null, null, null, null, null, "BIZ_001", null, null, null,
                null, List.of(line), null, null, null, null, null, null, TENANT_ID);
    }
}
