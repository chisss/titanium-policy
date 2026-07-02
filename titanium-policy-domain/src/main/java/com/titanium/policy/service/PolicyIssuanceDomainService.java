package com.titanium.policy.service;

import com.titanium.policy.aggregate.Insurance;
import com.titanium.policy.valueobject.insurance.PolicyIssuanceDecision;
import com.titanium.policy.valueobject.insurance.UnderwritingResult;

/**
 * 承保领域服务
 * <p>
 * 承载「投保单 + 核保结果 → 能否承保及保单构建要素」这一<b>跨聚合的纯业务规则</b>：
 * 逻辑横跨 {@code Insurance}（投保单聚合）与核保结论 {@code UnderwritingResult}，
 * 不属于任何单一聚合根，故落在领域服务而非聚合根。
 * </p>
 * <p>
 * 🔴 领域服务铁律（本接口及其实现严格遵守）：
 * <ul>
 *     <li>入参/出参只允许聚合根、实体、值对象、枚举；</li>
 *     <li>无状态、纯计算，给定相同输入产出相同结果，可脱离 Spring 容器直测；</li>
 *     <li>不调用任何 Port（仓储/Feign/MQ）、不发命令（无 CommandGateway）、无事务、无 I/O。</li>
 * </ul>
 * 取数据、发 {@code CreatePolicyCommand}、事务等编排职责由应用层
 * {@code IssuanceOrchestrator} 承担，本服务只回答「业务上能不能承保、该保成什么样」。
 * </p>
 */
public interface PolicyIssuanceDomainService {

    /**
     * 依据投保单与核保结果推导承保决策
     *
     * @param insurance 投保单聚合（提供投保人、保费、保障期间等承保要素）
     * @param underwritingResult 核保结果（提供核保结论与承保条件）
     * @return 承保决策：可否承保 + 可承保时的保单构建要素
     */
    PolicyIssuanceDecision decideIssuance(Insurance insurance, UnderwritingResult underwritingResult);

    /**
     * 仅依据核保结论判定能否承保（不依赖投保单聚合的承保准入规则）
     * <p>
     * 供事件驱动编排（{@code IssuanceSaga}）在仅持有核保结论、尚未加载投保单聚合时做流程路由：
     * 核保通过则推进承保出单，否则结束流程。与 {@link #decideIssuance} 共用同一套「哪些结论算通过」
     * 的准入规则，保证同步编排与异步编排裁决口径一致。
     * </p>
     *
     * @param underwritingResult 核保结果
     * @return 能否承保
     */
    boolean canIssueByConclusion(UnderwritingResult underwritingResult);
}
