package com.titanium.policy.valueobject;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.titanium.metadata.enums.billing.PremiumCollectionMode;
import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.IssuanceStrategy;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.SalesChannel;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.entity.insurance.InsuredPartyList;

/**
 * 出单请求值对象
 * <p>
 * 🔴 <b>核心改动：以结构化方案行 {@link #planLines} 承载多险种</b>。改造前本记录仅 11 个扁平字段
 * （单 productId + 单 totalPremium），<b>缺保额、缴费模式、缴费年数、被保险人清单、受益人清单、
 * 附加险</b>，导致两处失效：
 * </p>
 * <ol>
 *   <li>两步出单构造投保单命令时 {@code sumInsured}/{@code paymentMode} 只能传 null，进而
 *       出单 Saga 的 {@code sumInsured != null} 判断恒假，<b>真实保费计算被整体跳过</b>，
 *       只能回退上游传入的报价（假保费）；</li>
 *   <li>无法表达一单多险——一张保单只能承保一个产品。</li>
 * </ol>
 * <p>
 * 字段按「单据级 vs 段级」分层：投保人、受益人、收费方式、保单主期间是<b>单据级</b>（跨段共用）；
 * 保额、保障期间、缴费条件、标的是<b>段级</b>（各段独立，见 {@link IssuancePlanLine}）。
 * </p>
 *
 * @param bizNo            调用方业务流水号（同租户内唯一，重复提交返回首次结果）
 * @param tenantId         租户ID
 * @param userId           发起投保的注册用户ID（后台代录场景可空）
 * @param marketPackageId  营销包ID（弱引用，可空）
 * @param issuanceStrategy 出单策略（合并为一张多险种保单 / 拆分为多张保单）
 * @param holderCustomerId 投保人客户ID（已由 customer 域 upsert 回填）
 * @param insuredPartyList 参与方清单（投保人 + 被保险人 + 受益人，含受益顺位与份额）
 * @param policyForm       保单形态（缺省由主险产品配置推导）
 * @param insuranceType    主险险种三级分类（缺省由产品推导）
 * @param periodStart      保单主保障起期
 * @param periodEnd        保单主保障止期
 * @param collectionMode   收费方式（单据级，一次收总额）
 * @param channelId        渠道ID（指向 channel 域）
 * @param salesChannel     销售渠道大类
 * @param agentId          代理人/业务员ID
 * @param planLines        出单方案行列表（险种段级要素，1..N，至少一个 MAIN）
 * @param quotedPremium    上游报价（仅用于与系统试算比对，<b>不作为保费真相</b>）
 * @param extendData       单据级扩展要素（本期透传存档）
 */
public record IssuanceRequest(String bizNo, String tenantId, String userId, String marketPackageId,
                              IssuanceStrategy issuanceStrategy, String holderCustomerId,
                              InsuredPartyList insuredPartyList, PolicyForm policyForm,
                              InsuranceProductType insuranceType, LocalDateTime periodStart, LocalDateTime periodEnd,
                              PremiumCollectionMode collectionMode, String channelId, SalesChannel salesChannel,
                              String agentId, List<IssuancePlanLine> planLines, Money quotedPremium,
                              Map<String, Object> extendData) {

    /**
     * 主险方案行（有且仅有一个）。
     *
     * @return 主险方案行；无方案行时返回 null
     */
    public IssuancePlanLine mainLine() {
        if (planLines == null) {
            return null;
        }
        return planLines.stream().filter(IssuancePlanLine::isMain).findFirst().orElse(null);
    }

    /**
     * 附加险方案行列表。
     *
     * @return 附加险方案行列表
     */
    public List<IssuancePlanLine> riderLines() {
        if (planLines == null) {
            return List.of();
        }
        return planLines.stream().filter(IssuancePlanLine::isRider).toList();
    }

    /**
     * 主险产品ID（出单模式路由与产品配置加载的依据）。
     *
     * @return 主险产品ID；无主险段时返回 null
     */
    public String mainProductId() {
        IssuancePlanLine main = mainLine();
        return main != null ? main.productId() : null;
    }

    /**
     * 主险投保保额（保费试算入参）。
     *
     * @return 主险保额；无主险段时返回 null
     */
    public Money mainSumInsured() {
        IssuancePlanLine main = mainLine();
        return main != null ? main.sumInsured() : null;
    }

    /**
     * 被保险人数（取参与方清单的被保险人数量）。
     *
     * @return 被保险人数
     */
    public int insuredCount() {
        if (insuredPartyList == null || insuredPartyList.insuredList() == null) {
            return 0;
        }
        return insuredPartyList.insuredList().size();
    }

    /**
     * 各被保险人年龄（取参与方清单，供投保年龄校验与保费试算使用）。
     * <p>
     * 与 {@code IssuancePlanLine.subjectAttributeInts("age")} 的分工：段内标的属性优先
     * （多标的段各标的年龄不同），本方法作单据级回退（寿险/医疗险常见的「标的即被保险人」场景）。
     * </p>
     *
     * @return 年龄列表；无被保险人时返回空列表
     */
    public List<Integer> insuredAges() {
        if (insuredPartyList == null || insuredPartyList.insuredList() == null) {
            return List.of();
        }
        List<Integer> ages = new ArrayList<>();
        for (InsuredPartyList.InsuredInfo insured : insuredPartyList.insuredList()) {
            ages.add(insured.age());
        }
        return List.copyOf(ages);
    }

    /**
     * 是否为拆分出单（一次请求产出多张保单）。
     *
     * @return 拆分策略返回 {@code true}
     */
    @JsonIgnore
    public boolean isSplitStrategy() {
        return issuanceStrategy == IssuanceStrategy.SPLIT_MULTI_POLICY;
    }
}
