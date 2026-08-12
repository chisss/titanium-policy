package com.titanium.policy.service;

import java.util.List;

import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.entity.policy.PolicyProduct;
import com.titanium.policy.valueobject.RuleDecision;

/**
 * 保单构成领域服务（纯领域服务）
 * <p>
 * 承载「多个险种段能否构成一张合法保单」的<b>跨段规则</b>——这些规则不属于任何单个险种段
 * （段自身无法知道兄弟段的存在），也不涉及取数或发命令，符合纯领域服务的「三无 + 一不属于」判据：
 * 无 {@code CommandGateway}、无外部 Port、无基础设施依赖，且跨多个实体。
 * </p>
 * <p>
 * 四条不变量（对应设计文档 4.3）：
 * </p>
 * <ol>
 *   <li><b>唯一主险</b>：有且仅有一个 MAIN 段；</li>
 *   <li><b>附加险依附合法</b>：每个 RIDER 的 {@code parentPolicyProductId} 指向该 MAIN 段；</li>
 *   <li><b>保费守恒</b>：保单总保费 = Σ 计入段的保费（拒保段不计入）；</li>
 *   <li><b>段ID唯一</b>：段ID与段序号在保单内不重复。</li>
 * </ol>
 * <p>
 * 「RIDER 产品是否在主险 {@code attachProductIds} 内」需查产品域配置，属取数职责，
 * 由 application 编排取数后交 {@code IssuanceEligibilityDomainService} 裁决，不在本服务。
 * </p>
 */
public interface PolicyCompositionDomainService {

    /**
     * 校验险种段列表能否构成一张合法保单。
     *
     * @param lines        险种段列表
     * @param totalPremium 保单声明的总保费（为 null 时跳过保费守恒校验）
     * @return 构成决策（通过 / 首个违反的不变量及说明）
     */
    RuleDecision validate(List<PolicyProduct> lines, Money totalPremium);

    /**
     * 计算险种段列表的有效保费合计（拒保段不计入）。
     *
     * @param lines 险种段列表
     * @return 保费合计；无有效段时返回 null
     */
    Money sumPremium(List<PolicyProduct> lines);
}
