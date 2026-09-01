package com.titanium.policy.port;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 保费计算网关端口（driven port，与聚合平级）
 * <p>
 * 出单 Saga 经此端口向 billing 域请求标准保费计算，由 infrastructure 的
 * {@code PremiumCalculationAdapter} 调 billing 的 {@code PremiumCalculationApi} 实现。
 * 出参为防腐层值对象，屏蔽 billing API 细节。
 * </p>
 */
public interface PremiumCalculationGateway {

    /**
     * 计算标准保费。
     *
     * @param request 保费计算请求
     * @return 计算结果；若远程失败则抛异常（调用方应捕获后回退 exactPremium）
     */
    StandardPremiumResult calculatePremium(StandardPremiumRequest request);

    /**
     * 保费计算请求（策略域值对象）。
     *
     * @param productId          产品ID
     * @param sumInsured         基本保额
     * @param currency           币种
     * @param paymentMode        缴费模式 code
     * @param totalPeriods       总缴费期数
     * @param coverageYears      保障期（年）
     * @param subjectData        被保人要素（age/gender 等）
     * @param tenantId           租户ID
     */
    record StandardPremiumRequest(
            String productId,
            BigDecimal sumInsured,
            String currency,
            String paymentMode,
            int totalPeriods,
            int coverageYears,
            Map<String, Object> subjectData,
            String tenantId
    ) {}

    /**
     * 保费计算结果（保单域值对象，防腐层翻译自 billing 响应）。
     *
     * @param totalPremium      应付总保费
     * @param installmentAmount 每期应缴金额
     * @param periods           期数
     * @param currency          币种
     */
    record StandardPremiumResult(
            BigDecimal totalPremium,
            BigDecimal installmentAmount,
            int periods,
            String currency
    ) {}
}
