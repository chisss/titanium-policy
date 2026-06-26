package com.titanium.policy.service;

import com.titanium.policy.valueobject.insurance.UnderwritingDecisionRequest;
import com.titanium.policy.valueobject.insurance.UnderwritingResult;

/**
 * 核保决策网关端口
 * <p>
 * 投保出单 Saga 经此端口向核保域请求核保结论，由基础设施/应用层适配器实现具体通信。
 * 端口面向领域语义（入参 {@link UnderwritingDecisionRequest}、出参 {@link UnderwritingResult}），
 * 屏蔽底层是同步 Feign 还是异步消息：
 * </p>
 * <ul>
 *     <li>当前阶段：注册中心/消息总线未就绪，由 {@code SyncUnderwritingDecisionAdapter} 同步调用核保服务；</li>
 *     <li>后续演进：基础设施就绪后可替换为发命令 + 监听 {@code UnderwritingResultReceivedEvent} 的异步实现，
 *         Saga 编排逻辑无需改动。</li>
 * </ul>
 */
public interface UnderwritingDecisionGateway {

    /**
     * 请求核保结论
     *
     * @param request 核保决策请求
     * @return 核保结果（通过/拒绝/暂缓及承保条件）
     */
    UnderwritingResult requestDecision(UnderwritingDecisionRequest request);
}
