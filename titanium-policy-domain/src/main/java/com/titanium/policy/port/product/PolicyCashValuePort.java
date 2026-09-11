package com.titanium.policy.port.product;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import com.titanium.policy.valueobject.product.PolicyCashValue;

/**
 * 保单现金价值端口（driven port，与聚合平级）。
 * <p>
 * 保单域是「保单要素」的权威（保单年度、保费基数、生效日），产品域是「退保价值规则」的权威
 * （按产品 + 保单年度配置现金价值率）。本端口表达保单域需要产品域提供的能力：给定保单要素，
 * 按产品域当前生效的退保价值策略算出该时点现金价值。
 * </p>
 * <p>
 * 调用方（计费域的垫缴判定等）只需保单号，由保单域承担「保单号 → 保单要素 → 现金价值」的
 * 全部翻译，避免各消费方各自重复推导保单年度口径。
 * </p>
 */
public interface PolicyCashValuePort {

    /**
     * 查保单现金价值（只读，不产生任何落库副作用）。
     *
     * @param tenantId 租户ID
     * @param productId 产品ID
     * @param policyYear 保单年度（自 1 起）
     * @param customerPayable 客户应付保费基数（保单域传保单总保费，与退保试算口径一致）
     * @param policyEffectiveDate 保单生效日（犹豫期判定基准）
     * @param valuationDate 估值日
     * @return 现金价值；产品域无适用策略或调用失败时返回空（由调用方决定降级语义）
     */
    Optional<PolicyCashValue> getCashValue(String tenantId, String productId, int policyYear,
            BigDecimal customerPayable, LocalDate policyEffectiveDate, LocalDate valuationDate);
}
