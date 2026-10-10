package com.titanium.policy.valueobject.product;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 某产品全部责任的保额档次配置（档次判定的载体）
 * <p>
 * 投保单段级保额校验的入参：把「产品 → 各责任档次配置」的对应关系显式承载为值对象，避免用
 * {@code Map<String, List<...>>} 之类的弱类型结构穿过命令与聚合。
 * </p>
 * <p>
 * 🔴 <b>判定为「存在性量化」，不是逐责任匹配</b>：投保单险种段 {@code InsuranceLine} 只到
 * <b>产品</b>粒度（段上无 {@code coverageCode}，见 {@code InsuranceLine} 的字段构成），故对本产品
 * 已配置的 N 条责任只能问「<b>是否存在</b>至少一条责任接纳该保额」（∃，存在量化）。
 * 这与「保额恰好落在它实际对应的那条责任的档次上」并非等价语义——后者需要段级携带责任编码，
 * 属建模变更，不在本任务范围（见 {@code G02/AC-09} 的落点裁决：投保单段级）。本限制**必须**
 * 在验收判据与调用方认知中保持一致，不得静默当作逐责任校验。
 * </p>
 * <p>
 * 语义边界（放行 / 拒绝各有明确出处）：
 * </p>
 * <ul>
 *   <li><b>该 productId 不在本载体集合中</b>（产品未配置任何责任档次）⇒ 放行 + 留痕：
 *       G01 已定「不配置是合法的中间状态」，若在此拒绝会让存量未配置档次的产品全部无法出单，
 *       属未经确认的破坏性变更；</li>
 *   <li><b>配置存在但一条责任都不接纳</b> ⇒ 拒绝（携带各责任可选档位/区间便于改选）；</li>
 *   <li><b>整个载体集合为 {@code null}</b>（调用方未提供配置）⇒ 由聚合拒绝（fail-closed），
 *       防止新增建单入口漏传配置后静默绕过档次校验；显式传空集合表示「已知该产品未配置档次」，
 *       与「没接线」不同形也不同义。</li>
 * </ul>
 *
 * @param productId 产品ID（与 {@code InsuranceLine.productId()} 对齐）
 * @param coverages 该产品已配置的责任档次清单（可为空，表示「查询成功但未配置任何责任档次」）
 */
public record ProductSumInsuredTiers(String productId, List<CoverageSumInsuredConfig> coverages) {

    /**
     * 紧凑构造器：清单不可变化。
     */
    public ProductSumInsuredTiers {
        coverages = coverages == null ? List.of() : List.copyOf(coverages);
    }

    /**
     * 是否存在至少一条责任接纳该保额（存在量化，∃）。
     *
     * @param sumInsured 投保单段级录入保额
     * @return 存在接纳的责任返回 {@code true}
     */
    public boolean acceptsSumInsured(BigDecimal sumInsured) {
        return coverages.stream().anyMatch(coverage -> coverage.accepts(sumInsured));
    }

    /**
     * 是否已配置责任档次（空清单 = 未配置，非「配置为空」）。
     *
     * @return 已配置返回 {@code true}
     */
    public boolean hasConfig() {
        return !coverages.isEmpty();
    }

    /**
     * 全部责任的可填保额说明（拒绝文案的渲染参数）。
     *
     * @return 各责任说明以 {@code " | "} 连接；未配置时返回 {@code []}
     */
    public String describeOptions() {
        return coverages.stream()
                .map(CoverageSumInsuredConfig::describeOptions)
                .collect(Collectors.joining(" | ", "[", "]"));
    }
}
