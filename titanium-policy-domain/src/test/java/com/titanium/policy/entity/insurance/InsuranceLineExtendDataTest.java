package com.titanium.policy.entity.insurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.policy.PolicyLineStatus;
import com.titanium.metadata.enums.product.ProductEnum.PaymentFrequency;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.valueobject.policy.LineCoveragePeriod;
import com.titanium.policy.valueobject.policy.LinePaymentTerms;

/**
 * 投保段扩展数据保真测试（G12/g12-01 AC-01）
 * <p>
 * 段级 {@code extendData} 是健康告知等特化要素的透传载体。本类锁死三件事：① 构造时携带即原样可读；
 * ② 兼容构造器（历史事件回放路径）缺省为 {@code null} 而非失败；③ <b>三个 wither 均保真</b>——
 * 核保结论回写 / 保费试算 / 保费确认三条路径各自重建段对象，任何一条漏带 extendData，
 * 健康告知都会在承保中途静默丢失（且链路其余部分照常运转、无从察觉）。
 * </p>
 */
class InsuranceLineExtendDataTest {

    /** 段级扩展数据样例（块结构与 PolicyConstants.LineExtendDataKey 契约同形） */
    private static final Map<String, Object> EXTEND_DATA =
            Map.of("healthDeclaration", Map.of("smoking", true, "heightCm", 175, "weightKg", 80));

    @Test
    void keepsExtendDataProvidedAtConstruction() {
        assertEquals(EXTEND_DATA, line(EXTEND_DATA).extendData());
    }

    @Test
    void defaultsExtendDataToNullForLegacyConstructor() {
        // 兼容构造器供历史事件回放使用：缺省为 null，不得抛错（历史事件 JSON 无该字段）
        InsuranceLine legacy = new InsuranceLine("LINE_001", 1, ProductCategory.MAIN, null, "PRODUCT_ID_001",
                "PRODUCT_CODE_001", "测试产品", null, Money.of(new BigDecimal("500000"), "CNY"),
                Money.of(new BigDecimal("1000"), "CNY"), coveragePeriod(),
                new LinePaymentTerms(PaymentFrequency.ANNUAL, 20), List.of(), null, null,
                PolicyLineStatus.UNDERWRITING);

        assertNull(legacy.extendData());
    }

    @Test
    void withersPreserveExtendDataAcrossUnderwritingAndPremiumHandbacks() {
        InsuranceLine line = line(EXTEND_DATA);

        // 核保结论回写（Saga 收到结论后重建段）：不得把告知丢在回写路径上
        assertEquals(EXTEND_DATA,
                line.withUnderwritingResult(ConclusionType.MODIFY, new BigDecimal("0.3")).extendData());
        // 保费试算写回
        assertEquals(EXTEND_DATA, line.withPremium(Money.of(new BigDecimal("1200"), "CNY")).extendData());
        // 保费确认（该 wither 会清空核保结论与加费率，但 extendData 不在清理之列）
        assertEquals(EXTEND_DATA, line.withConfirmedPremium(Money.of(new BigDecimal("1200"), "CNY")).extendData());
    }

    // ---------------------------------------------------------------- 夹具

    private InsuranceLine line(Map<String, Object> extendData) {
        return new InsuranceLine("LINE_001", 1, ProductCategory.MAIN, null, "PRODUCT_ID_001", "PRODUCT_CODE_001",
                "测试产品", "V1.0", null, Money.of(new BigDecimal("500000"), "CNY"),
                Money.of(new BigDecimal("1000"), "CNY"), coveragePeriod(),
                new LinePaymentTerms(PaymentFrequency.ANNUAL, 20), List.of(), ConclusionType.ACCEPT, null,
                PolicyLineStatus.UNDERWRITING, extendData);
    }

    private LineCoveragePeriod coveragePeriod() {
        return LineCoveragePeriod.fixedTerm(LocalDateTime.now(), LocalDateTime.now().plusYears(1), null, null);
    }
}
