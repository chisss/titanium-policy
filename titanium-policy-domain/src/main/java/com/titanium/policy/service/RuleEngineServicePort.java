package com.titanium.policy.service;

import java.util.Map;

/**
 * 规则引擎服务端口
 * 定义规则引擎服务的接口，由应用层实现
 */
public interface RuleEngineServicePort {
    /**
     * 执行规则
     */
    Object executeRule(String ruleSetCode, Map<String, Object> variables, String tenantId);

    /**
     * 验证规则
     */
    Object validateRule(String ruleSetCode, Map<String, Object> variables, String tenantId);
}