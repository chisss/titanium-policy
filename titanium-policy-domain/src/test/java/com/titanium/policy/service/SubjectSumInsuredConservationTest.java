package com.titanium.policy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.insurance.SubjectType;
import com.titanium.metadata.enums.policy.PolicyLineStatus;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.valueobject.Money;
import com.titanium.metadata.valueobject.RuleDecision;
import com.titanium.policy.entity.policy.InsuredSubject;
import com.titanium.policy.entity.policy.PolicyProduct;
import com.titanium.policy.service.impl.PolicyCompositionDomainServiceImpl;

/**
 * 多标的分项保额守恒不变量测试（G11/g11-05，AC-07）。
 * <p>
 * 验收命令（G11 文档写死）：
 * {@code mvn -pl titanium-policy-domain test -Dtest=SubjectSumInsuredConservationTest}。
 * </p>
 * <p>
 * 验证 {@code PolicyCompositionDomainServiceImpl} 不变量⑤：<b>多标的段</b>的 Σ 标的保额 == 段保额
 * （容差 1 分）。用例按「正反成对」组织，覆盖四条口径：
 * </p>
 * <ol>
 *   <li>多标的段守恒放行 / 不等被拒（小于与大于两个方向）；</li>
 *   <li>单标的段（含无标的段）<b>不校验</b>——维持「可空 = 取段保额」语义，防误伤存量；</li>
 *   <li>标的保额为 null 时<b>按段保额代入</b>——与 {@code InsuranceLineAssembler.toSubject} 装配口径
 *       逐字一致（含 null 的最小定因用例：若口径漂移为「null 按 0 计」该用例会由红转绿）；</li>
 *   <li>容差边界：差额恰 1 分放行、超出即拒。</li>
 * </ol>
 */
class SubjectSumInsuredConservationTest {

    private final PolicyCompositionDomainService service = new PolicyCompositionDomainServiceImpl();

    // ────────────────────────── 构造辅助 ──────────────────────────

    /** 带显式保额的物类标的（车/财产，无客户主数据引用）。 */
    private static InsuredSubject subject(String id, String sumInsured) {
        return InsuredSubject.ofObject(id, SubjectType.VEHICLE, "标的" + id,
                sumInsured != null ? Money.of(new BigDecimal(sumInsured), "CNY") : null, null);
    }

    /** 构造主险段（段保额显式，标的列表显式）。 */
    private static PolicyProduct mainLine(int lineNo, String lineSumInsured, List<InsuredSubject> subjects) {
        return line("L" + lineNo, lineNo, null, lineSumInsured, subjects);
    }

    /** 构造附加险段（依附主险段 L1）。 */
    private static PolicyProduct riderLine(int lineNo, String lineSumInsured, List<InsuredSubject> subjects) {
        return line("L" + lineNo, lineNo, "L1", lineSumInsured, subjects);
    }

    private static PolicyProduct line(String id, int lineNo, String parentId, String lineSumInsured,
                                      List<InsuredSubject> subjects) {
        return new PolicyProduct(id, lineNo, parentId != null ? ProductCategory.RIDER : ProductCategory.MAIN,
                parentId, "prod-" + id, "CODE-" + id, "产品" + id, "V1.0",
                InsuranceProductType.MEDICAL,
                lineSumInsured != null ? Money.of(new BigDecimal(lineSumInsured), "CNY") : null,
                null, null, null, null, PolicyLineStatus.ACCEPTED,
                List.of(), subjects, List.of());
    }

    // ────────────────────────── 口径①：多标的段守恒 ──────────────────────────

    @Test
    @DisplayName("多标的段 Σ 标的保额 == 段保额，放行")
    void shouldAcceptConservedMultiSubjectLine() {
        PolicyProduct line = mainLine(1, "1000000",
                List.of(subject("S1", "300000"), subject("S2", "300000"), subject("S3", "400000")));

        RuleDecision decision = service.validate(List.of(line), null);

        assertTrue(decision.passed(), "分项合计恰等于段保额应放行: " + decision.defaultMessage());
        assertNull(decision.errorCode());
    }

    @Test
    @DisplayName("多标的段小数分摊（33.33+33.33+33.34 == 100）放行")
    void shouldAcceptConservedWithDecimalAllocation() {
        PolicyProduct line = mainLine(1, "100",
                List.of(subject("S1", "33.33"), subject("S2", "33.33"), subject("S3", "33.34")));

        RuleDecision decision = service.validate(List.of(line), null);

        assertTrue(decision.passed(), "分摊舍入后精确相等应放行: " + decision.defaultMessage());
    }

    @Test
    @DisplayName("多标的段 Σ 小于段保额，被拒（反向）")
    void shouldRejectWhenSubjectSumBelowLine() {
        PolicyProduct line = mainLine(1, "1000000",
                List.of(subject("S1", "300000"), subject("S2", "300000"), subject("S3", "300000")));

        RuleDecision decision = service.validate(List.of(line), null);

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.COMPOSITION_SUBJECT_SUM_INSURED_NOT_CONSERVED, decision.errorCode());
        assertEquals(1, decision.lineNo(), "段级违反须携带段序号，便于一单多险定位");
    }

    @Test
    @DisplayName("多标的段 Σ 大于段保额，被拒（反向）")
    void shouldRejectWhenSubjectSumExceedsLine() {
        PolicyProduct line = mainLine(1, "1000000",
                List.of(subject("S1", "300000"), subject("S2", "300000"), subject("S3", "300000"),
                        subject("S4", "300000")));

        RuleDecision decision = service.validate(List.of(line), null);

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.COMPOSITION_SUBJECT_SUM_INSURED_NOT_CONSERVED, decision.errorCode());
    }

    // ────────────────────────── 口径②：单标的段不校验（防误伤存量） ──────────────────────────

    @Test
    @DisplayName("单标的段标保额 != 段保额也不校验（「可空 = 取段保额」语义，防误伤存量）")
    void shouldAcceptSingleSubjectLineEvenIfNotEqual() {
        PolicyProduct line = mainLine(1, "1000000", List.of(subject("S1", "800000")));

        RuleDecision decision = service.validate(List.of(line), null);

        assertTrue(decision.passed(), "单标的段的标保额与段保额可不一致（现有语义不变）: "
                + decision.defaultMessage());
    }

    @Test
    @DisplayName("单标的段标保额为 null 放行（仍按「取段保额」语义）")
    void shouldAcceptSingleSubjectLineWithNullSumInsured() {
        PolicyProduct line = mainLine(1, "1000000", List.of(subject("S1", null)));

        RuleDecision decision = service.validate(List.of(line), null);

        assertTrue(decision.passed(), "单标的段标的保额可空: " + decision.defaultMessage());
    }

    @Test
    @DisplayName("无标的段放行（标的不适用该险种，非本不变量范围）")
    void shouldAcceptLineWithoutSubject() {
        PolicyProduct line = mainLine(1, "1000000", List.of());

        RuleDecision decision = service.validate(List.of(line), null);

        assertTrue(decision.passed(), "无标的段不适用分项守恒: " + decision.defaultMessage());
    }

    // ────────────────────────── 口径③：null 按段保额代入（与装配器同口径） ──────────────────────────

    @Test
    @DisplayName("多标的段含 null 保额（null/50/50 vs 段 100）被拒——null 按段保额代入的最小定因")
    void shouldRejectMultiSubjectLineWithNullSumInsured() {
        // 🔴 最小定因样本：若 null 口径漂移为「按 0 计」，Σ=0+50+50=100 恰等于段保额、本用例将由红转绿。
        // 口径依据：InsuranceLineAssembler.toSubject 对 null 即取段保额，校验端逐字一致。
        PolicyProduct line = mainLine(1, "100",
                List.of(subject("S1", null), subject("S2", "50"), subject("S3", "50")));

        RuleDecision decision = service.validate(List.of(line), null);

        assertFalse(decision.passed(), "多标的段任一标的未声明保额必不守恒（null 代入段保额）");
        assertEquals(PolicyErrorCode.COMPOSITION_SUBJECT_SUM_INSURED_NOT_CONSERVED, decision.errorCode());
    }

    // ────────────────────────── 口径④：容差 1 分 ──────────────────────────

    @Test
    @DisplayName("差额恰 1 分（50.00+49.99 vs 100.00）放行（容差边界内）")
    void shouldAcceptWithinEpsilon() {
        PolicyProduct line = mainLine(1, "100.00", List.of(subject("S1", "50.00"), subject("S2", "49.99")));

        RuleDecision decision = service.validate(List.of(line), null);

        assertTrue(decision.passed(), "分摊舍入误差 0.01 元内应容忍: " + decision.defaultMessage());
    }

    @Test
    @DisplayName("差额 2 分（50.00+49.98 vs 100.00）被拒（超容差）")
    void shouldRejectBeyondEpsilon() {
        PolicyProduct line = mainLine(1, "100.00", List.of(subject("S1", "50.00"), subject("S2", "49.98")));

        RuleDecision decision = service.validate(List.of(line), null);

        assertFalse(decision.passed(), "超出 1 分容差应拒绝");
        assertEquals(PolicyErrorCode.COMPOSITION_SUBJECT_SUM_INSURED_NOT_CONSERVED, decision.errorCode());
    }

    // ────────────────────────── 段保额缺失让路（javadoc 口径第 4 条） ──────────────────────────

    @Test
    @DisplayName("多标的段但段保额缺失（null）跳过校验——归出单要素校验兜底，不 NPE 不误拒")
    void shouldSkipMultiSubjectLineWithNullLineSumInsured() {
        // 防御分支：上游漏传段保额时本不变量让路（由 ELIGIBILITY_SUM_INSURED_REQUIRED 拦截），
        // 若去掉 null 检查则 lineSumInsured.value() 直接 NPE——把「参数错」劣化为「服务 500」。
        PolicyProduct line = mainLine(1, null,
                List.of(subject("S1", "300000"), subject("S2", "300000")));

        RuleDecision decision = service.validate(List.of(line), null);

        assertTrue(decision.passed(), "段保额缺失非本不变量职责，应跳过: " + decision.defaultMessage());
    }

    // ────────────────────────── 遍历与编排顺序 ──────────────────────────

    @Test
    @DisplayName("多段遍历：第二段（附加险）不守恒被拒，段序号精确指向该段")
    void shouldReportFailingLineNoForSecondLine() {
        PolicyProduct main = mainLine(1, "1000000", List.of(subject("S1", null)));
        PolicyProduct rider = riderLine(2, "1000000",
                List.of(subject("S2", "300000"), subject("S3", "300000")));

        RuleDecision decision = service.validate(List.of(main, rider), null);

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.COMPOSITION_SUBJECT_SUM_INSURED_NOT_CONSERVED, decision.errorCode());
        assertEquals(2, decision.lineNo(), "应精确指向第二段（附加险），而非笼统报整单");
    }

    @Test
    @DisplayName("不变量⑤先于④：段不守恒且总保费也不守恒时，报分项保额违反（顺序即契约）")
    void shouldReportSubjectSumInsuredViolationBeforePremiumViolation() {
        PolicyProduct line = mainLine(1, "1000000",
                List.of(subject("S1", "300000"), subject("S2", "300000")));

        // 总保费声明为 9999（与段保费 0/null 均不守恒），若执行顺序颠倒会先报 COMPOSITION_PREMIUM_NOT_CONSERVED
        RuleDecision decision = service.validate(List.of(line), Money.of(new BigDecimal("9999"), "CNY"));

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.COMPOSITION_SUBJECT_SUM_INSURED_NOT_CONSERVED, decision.errorCode(),
                "分项保额守恒（结构规则）应先于保费守恒（金额规则）裁决");
    }
}
