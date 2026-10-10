package com.titanium.policy.valueobject.product;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 责任级保额档次配置（product 域契约的保单域防腐镜像）
 * <p>
 * 镜像 product 域 {@code CoverageSumInsuredConfig} / api 契约
 * {@code CoverageSumInsuredConfigResponse}：表达「某产品的某条保障责任，投保时保额可以怎么填」——
 * 可选档位集合 + 允许的保额区间 + 是否开放自由填 + 默认档位。由基础设施层
 * {@code ProductServiceAdapter} 经 Feign 取回后翻译成本类型（防腐层，不依赖 product 域类型）。
 * </p>
 * <p>
 * 🔴 <b>本类只承载数据 + 单条责任的接纳判定，不重复 product 侧的配置合法性校验</b>：七条不变量
 * （档位非空/单调、区间自洽、档位落区间、默认档位唯一等）由 product 域在写入时守护，镜像侧不再判一遍
 * （重复实现必然与对端漂移）。对端新增分量时，本类须同批补齐——字段对齐由
 * {@code CoverageSumInsuredMirrorAlignmentTest} 以「读对端 api 契约源码逐分量比对」的方式固化。
 * </p>
 * <p>
 * 🔴 <b>{@link #accepts(BigDecimal)} 是 product 侧 {@code resolveSelection} 判定次序的对端一致性实现</b>：
 * 命中档位（无论是否开放自由填）→ 未命中且不开放自由填则拒 → 未命中且开放自由填再判区间。
 * 次序不可调整——先判档位才能让「不开放自由填」场景的拒绝点明「未命中档位」而非笼统的「超区间」。
 * 对端若调整判定次序，本实现的用例须同步（两处各写一份清单必漂移，故配行为对齐用例锁死）。
 * </p>
 *
 * @param coverageCode      责任编码（同一产品下唯一）
 * @param tiers             档位清单（按保额严格递增，非空）
 * @param minSumInsured     允许的保额区间下界
 * @param maxSumInsured     允许的保额区间上界
 * @param allowCustomAmount 是否允许录入档位之外、区间之内的保额
 * @param defaultTierCode   默认档位编码
 */
public record CoverageSumInsuredConfig(String coverageCode,
                                       List<SumInsuredTier> tiers,
                                       BigDecimal minSumInsured,
                                       BigDecimal maxSumInsured,
                                       boolean allowCustomAmount,
                                       String defaultTierCode) {

    /**
     * 紧凑构造器：仅做不可变化与空清单归一（不复制对端的不变量校验）。
     */
    public CoverageSumInsuredConfig {
        tiers = tiers == null ? List.of() : List.copyOf(tiers);
    }

    /**
     * 判定一条录入保额是否被本责任接纳（不抛异常，供「多责任存在性量化」组合使用）。
     * <p>
     * 对应 product 域 {@code resolveSelection} 的三条拒绝情形：保额为空或非正、未命中档位且不开放
     * 自由填、超出允许区间。此处返回布尔而非异常——上层要的是「本责任是否接纳」，不是拒绝原因；
     * 拒绝原因由 {@link #describeOptions()} 汇总呈现。
     * </p>
     *
     * @param sumInsured 录入保额
     * @return 被本责任接纳返回 {@code true}
     */
    public boolean accepts(BigDecimal sumInsured) {
        if (sumInsured == null || sumInsured.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        for (SumInsuredTier tier : tiers) {
            if (tier.sumInsured() != null && tier.sumInsured().compareTo(sumInsured) == 0) {
                return true;
            }
        }
        if (!allowCustomAmount) {
            return false;
        }
        if (minSumInsured == null || maxSumInsured == null) {
            // 区间缺失时无法判「是否落在区间内」，按不接纳处理（失败关闭，不静默放行）
            return false;
        }
        return sumInsured.compareTo(minSumInsured) >= 0 && sumInsured.compareTo(maxSumInsured) <= 0;
    }

    /**
     * 本责任可填保额的说明（错误文案的渲染参数，供运营定位改选）。
     *
     * @return 形如 {@code 责任[COV-1] 档位[T1=100000, T2=500000] 区间[100000, 1000000] 自由填[是]}
     */
    public String describeOptions() {
        String tierText = tiers.stream()
                .map(tier -> tier.tierCode() + "=" + tier.sumInsured())
                .collect(Collectors.joining(", ", "[", "]"));
        return "责任[" + coverageCode + "] 档位" + tierText + " 区间[" + minSumInsured + ", " + maxSumInsured
                + "] 自由填[" + (allowCustomAmount ? "是" : "否") + "]";
    }

    /**
     * 保额档位（镜像 product 侧 {@code SumInsuredTier}）
     * <p>
     * 🔴 {@code allowedTerms} 与 {@code sortNo} <b>随契约承载但不参与本判定</b>：档位与保障期限的
     * 跨字段约束（如「30 万只能选 20 年」）归规则引擎（G09），不在承保前置链路里判。承载是为了
     * 「对端加字段即镜像对齐用例变红」，不让镜像静默缩窄。
     * </p>
     *
     * @param tierCode     档位编码（在所属责任内唯一）
     * @param sumInsured   档位保额
     * @param allowedTerms 该档位允许的保障期限（空列表表示不按期限限制，本判定不消费）
     * @param sortNo       展示排序号（仅呈现用，本判定不消费）
     */
    public record SumInsuredTier(String tierCode, BigDecimal sumInsured, List<Integer> allowedTerms,
                                 Integer sortNo) {
    }
}
