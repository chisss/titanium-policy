package com.titanium.policy.port.ruleengine;

import java.util.Map;

import com.titanium.policy.common.enums.RuleEngineDecision;

/**
 * 规则引擎服务端口
 * 定义规则引擎服务的接口，由应用层实现
 */
public interface RuleEngineServicePort {
    /**
     * 执行规则
     *
     * @param ruleSetCode 规则集编码
     * @param variables   规则变量上下文
     * @param tenantId    租户ID
     * @param businessId  关联业务单号（出单业务流水号，落规则引擎执行审计供按业务单反查；可空）
     * @return 规则引擎决策
     */
    RuleEngineDecision executeRule(String ruleSetCode, Map<String, Object> variables, String tenantId,
                                   String businessId);

    /**
     * 验证规则
     *
     * @param ruleSetCode 规则集编码
     * @param variables   规则变量上下文
     * @param tenantId    租户ID
     * @param businessId  关联业务单号（出单业务流水号，落规则引擎执行审计供按业务单反查；可空）
     * @return 校验是否通过
     */
    boolean validateRule(String ruleSetCode, Map<String, Object> variables, String tenantId, String businessId);
}
