package com.titanium.policy.valueobject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.product.ProductEnum;

/**
 * 出单流程配置 forMode 工厂测试
 * <p>
 * 锁定产品驱动出单的核心映射：出单模式 → 对应流程配置（风控步数、是否人工核保、是否自动激活）。
 * </p>
 */
class IssuanceProcessConfigTest {

    private static final String PRODUCT_CODE = "PROD-001";

    @Test
    @DisplayName("ONE_STEP：2 步风控、无人工核保、自动激活")
    void shouldBuildOneStepConfig() {
        IssuanceProcessConfig config = IssuanceProcessConfig.forMode(ProductEnum.IssuanceMode.ONE_STEP, PRODUCT_CODE);
        assertEquals(ProductEnum.IssuanceMode.ONE_STEP, config.issuanceMode());
        assertEquals(2, config.riskAssessmentSteps().size());
        assertFalse(config.requiresManualUnderwriting());
        assertTrue(config.autoActivate());
    }

    @Test
    @DisplayName("TWO_STEP：3 步风控、无人工核保、不自动激活")
    void shouldBuildTwoStepConfig() {
        IssuanceProcessConfig config = IssuanceProcessConfig.forMode(ProductEnum.IssuanceMode.TWO_STEP, PRODUCT_CODE);
        assertEquals(ProductEnum.IssuanceMode.TWO_STEP, config.issuanceMode());
        assertEquals(3, config.riskAssessmentSteps().size());
        assertFalse(config.requiresManualUnderwriting());
        assertFalse(config.autoActivate());
    }

    @Test
    @DisplayName("THREE_STEP：4 步风控、需人工核保")
    void shouldBuildThreeStepConfig() {
        IssuanceProcessConfig config = IssuanceProcessConfig.forMode(ProductEnum.IssuanceMode.THREE_STEP, PRODUCT_CODE);
        assertEquals(ProductEnum.IssuanceMode.THREE_STEP, config.issuanceMode());
        assertEquals(4, config.riskAssessmentSteps().size());
        assertTrue(config.requiresManualUnderwriting());
    }

    @Test
    @DisplayName("CUSTOM：暂不支持，抛出异常")
    void shouldRejectCustomMode() {
        assertThrows(IllegalArgumentException.class,
                () -> IssuanceProcessConfig.forMode(ProductEnum.IssuanceMode.CUSTOM, PRODUCT_CODE));
    }

    @Test
    @DisplayName("出单模式为空：抛出异常")
    void shouldRejectNullMode() {
        assertThrows(IllegalArgumentException.class, () -> IssuanceProcessConfig.forMode(null, PRODUCT_CODE));
    }
}
