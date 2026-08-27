package com.titanium.policy.service;

import com.titanium.policy.common.enums.RiskAssessmentStep;
import com.titanium.policy.common.enums.RuleEngineDecision;
import com.titanium.policy.valueobject.RiskAssessmentDecision;

/**
 * 风控裁决领域服务
 * <p>
 * 承载「风控步骤如何裁决」这一<b>纯领域规则</b>：给定风控步骤及其所需的外部裁决数据，
 * 判定该步骤是否通过。规则横跨多种风控环节（黑名单/重复投保/自动核保/反洗钱/健康告知），
 * 不属于任何单一聚合根，故落在领域服务。
 * </p>
 * <p>
 * 🔴 领域服务铁律（本接口及实现严格遵守）：
 * <ul>
 *     <li>入参/出参只允许枚举、值对象；</li>
 *     <li>无状态、纯计算，可脱离 Spring 容器直测；</li>
 *     <li>不调用任何 Port（规则引擎/客户域反欺诈等外部数据由应用层
 *         {@code RiskAssessmentExecutor} 经 Port 取得后作为入参喂入）。</li>
 * </ul>
 * 「取哪些外部数据」由 {@link #requiresRuleEngine(RiskAssessmentStep)} 声明，
 * 「取数动作」由应用层执行，本服务只负责「据数据裁决」。
 * </p>
 */
public interface RiskAssessmentDomainService {

    /**
     * 判定某风控步骤是否需要规则引擎外部裁决数据
     * <p>
     * 应用层据此决定是否调用规则引擎 Port 取数：返回 {@code true} 的步骤需先取得规则引擎裁决，
     * 再调 {@link #judge(RiskAssessmentStep, RuleEngineDecision)}；返回 {@code false} 的步骤为本地规则，
     * 直接调 {@link #judgeLocal(RiskAssessmentStep)}。
     * </p>
     *
     * @param step 风控步骤
     * @return 是否需要规则引擎数据
     */
    boolean requiresRuleEngine(RiskAssessmentStep step);

    /**
     * 依据规则引擎裁决结果对风控步骤作出领域裁决
     *
     * @param step 风控步骤（须为 {@link #requiresRuleEngine} 返回 true 的步骤）
     * @param ruleEngineDecision 规则引擎三态裁决
     * @return 风控裁决
     */
    RiskAssessmentDecision judge(RiskAssessmentStep step, RuleEngineDecision ruleEngineDecision);

    /**
     * 对不依赖外部数据的本地风控步骤作出领域裁决
     *
     * @param step 风控步骤（须为 {@link #requiresRuleEngine} 返回 false 的步骤）
     * @return 风控裁决
     */
    RiskAssessmentDecision judgeLocal(RiskAssessmentStep step);
}
