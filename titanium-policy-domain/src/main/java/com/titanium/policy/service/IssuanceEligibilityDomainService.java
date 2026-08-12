package com.titanium.policy.service;

import java.util.Map;

import com.titanium.policy.valueobject.IssuanceRequest;
import com.titanium.policy.valueobject.RuleDecision;
import com.titanium.policy.valueobject.product.ProductIssueRules;

/**
 * 投保要素校验领域服务（纯领域服务）
 * <p>
 * 承载「投保要素是否满足产品承保条件」的业务规则：投保年龄区间、保额上下限、职业黑名单、
 * 地域限制、被保险人数、保障期间合法性、缴费方式与年限、受益份额守恒、标的属性完整性。
 * 这些规则<b>跨投保请求与产品配置两侧</b>，不属于任何单个聚合根。
 * </p>
 * <p>
 * 🔴 <b>纯净性</b>：符合「三无 + 一不属于」判据——无 {@code CommandGateway}、无外部 Port、
 * 无基础设施依赖。产品配置以入参 {@link ProductIssueRules}（值对象）传入，取数由 application
 * 编排负责，本服务可脱离 Spring 用 {@code new} 直测。ArchUnit 第 8 条断言
 * {@code domain.service} 不依赖 {@code domain.port}——本服务仅引用 Port 的<b>内嵌 record</b>
 * （值对象），不引用 Port 接口本身。
 * </p>
 * <p>
 * 校验在<b>出单受理阶段</b>执行（编排流程第③步），不通过则同步拒绝，不产生任何单据。
 * 这与聚合根内的不变量校验分工不同：后者守护「已受理的数据结构合法」（如险种段构成），
 * 前者守护「业务条件满足」。
 * </p>
 */
public interface IssuanceEligibilityDomainService {

    /**
     * 校验出单请求是否满足产品承保条件。
     * <p>
     * 按「单据级规则 → 段级规则」顺序校验，返回<b>首个</b>不通过项（快速失败，便于调用方定位）。
     * 段级规则逐段校验，决策中携带违反所在的段序号。
     * </p>
     *
     * @param request        出单请求
     * @param rulesByProduct 各险种段产品的投保规则（键为 productId；缺失则跳过该段的产品级规则）
     * @return 校验决策（通过 / 首个违反项）
     */
    RuleDecision validate(IssuanceRequest request, Map<String, ProductIssueRules> rulesByProduct);
}
