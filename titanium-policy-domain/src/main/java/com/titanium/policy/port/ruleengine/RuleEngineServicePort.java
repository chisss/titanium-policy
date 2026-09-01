package com.titanium.policy.port;

import java.util.Map;

import com.titanium.policy.common.enums.RuleEngineDecision;

/**
 * 规则引擎服务端口
 * 定义规则引擎服务的接口，由应用层实现
 */
public interface RuleEngineServicePort {
    /**
     * 执行规则
     */
    RuleEngineDecision executeRule(String ruleSetCode, Map<String, Object> variables, String tenantId);

    /**
     * 验证规则
     */
    boolean validateRule(String ruleSetCode, Map<String, Object> variables, String tenantId);
}
