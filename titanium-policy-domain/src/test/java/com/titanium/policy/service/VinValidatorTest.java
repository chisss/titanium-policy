package com.titanium.policy.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.policy.service.impl.VinValidatorImpl;

/**
 * VIN 格式校验器测试（G11/g11-02，AC-01）。
 * <p>
 * 纯算法组件无依赖，直接 {@code new} 实例化。用例按「正向合法 / 反向非法」成对组织，
 * <b>每条反向用例都是最小定因样本</b>——只违反一条规则（长度 / 字符集 / 校验位），
 * 使失败时的红因指向明确规则而非笼统「不合法」。
 * </p>
 * <p>
 * 校验位样例的两个权威向量：{@code 1M8GDM9AXKP042788}（校验位 {@code X}，ISO 3779 公开
 * 示例：加权和 351 ≡ 10 (mod 11)）与 {@code LSVAA000201234567}（校验位 {@code 2}：加权和
 * 189 ≡ 2 (mod 11)，与 {@code IssuanceEligibilityDomainServiceTest} 的同源样例一致）。
 * </p>
 *
 * @author wei.sun
 * @since 2026/10/10
 * @see VinValidatorImpl
 */
class VinValidatorTest {

    private final VinValidator validator = new VinValidatorImpl();

    // ────────────────────────── 正向：合法 VIN ──────────────────────────

    @Test
    @DisplayName("合法 VIN（校验位 X）通过")
    void shouldAcceptValidVinWithCheckDigitX() {
        assertTrue(validator.isValid("1M8GDM9AXKP042788"), "公开权威样例，第 9 位 X 即校验位");
    }

    @Test
    @DisplayName("合法 VIN（校验位数字）通过")
    void shouldAcceptValidVinWithNumericCheckDigit() {
        assertTrue(validator.isValid("LSVAA000201234567"), "第 9 位 2 为校验位（189 mod 11 = 2）");
    }

    @Test
    @DisplayName("小写输入按大写宽容通过（录入习惯而非数据错误）")
    void shouldAcceptLowercaseInput() {
        assertTrue(validator.isValid("1m8gdm9axkp042788"));
    }

    @Test
    @DisplayName("全数字 VIN 通过（校验位 0）")
    void shouldAcceptAllDigitVin() {
        assertTrue(validator.isValid("00000000000000000"));
    }

    // ────────────────────────── 反向：非法 VIN（最小定因） ──────────────────────────

    @Test
    @DisplayName("null 拒绝")
    void shouldRejectNull() {
        assertFalse(validator.isValid(null));
    }

    @Test
    @DisplayName("空串与纯空白拒绝")
    void shouldRejectBlank() {
        assertFalse(validator.isValid(""));
        assertFalse(validator.isValid("   "));
    }

    @Test
    @DisplayName("长度不足 17 位拒绝（定因：长度）")
    void shouldRejectVinShorterThan17() {
        assertFalse(validator.isValid("1M8GDM9AXKP04278"), "16 位，其余各位与合法样例相同");
    }

    @Test
    @DisplayName("长度超过 17 位拒绝（定因：长度）")
    void shouldRejectVinLongerThan17() {
        assertFalse(validator.isValid("1M8GDM9AXKP0427881"), "18 位，前缀为完整合法样例");
    }

    @Test
    @DisplayName("校验位不符拒绝（定因：校验位；改动位权重为 0，不影响加权和）")
    void shouldRejectWrongCheckDigit() {
        assertFalse(validator.isValid("1M8GDM9A0KP042788"),
                "仅第 9 位由 X 改为 0——该位权重 0，加权和不变（期望仍为 X）");
    }

    @Test
    @DisplayName("含非法字符 I/O/Q 拒绝（ISO 3779 法定排除，防与 1/0 混淆）")
    void shouldRejectIllegalLetters() {
        assertFalse(validator.isValid("1M8GDM9AXKP04278I"));
        assertFalse(validator.isValid("1M8GDM9AXKP04278O"));
        assertFalse(validator.isValid("1M8GDM9AXKP04278Q"));
    }

    @Test
    @DisplayName("含非法符号拒绝（定因：字符集）")
    void shouldRejectIllegalSymbol() {
        assertFalse(validator.isValid("1M8GDM9AXKP04278-"));
    }
}
