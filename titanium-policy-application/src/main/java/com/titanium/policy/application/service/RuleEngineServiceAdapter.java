package com.titanium.policy.application.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.titanium.policy.service.RuleEngineServicePort;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 规则引擎服务适配器
 * 实现RuleEngineServicePort接口，适配RuleEngineService的调用
 */
@Slf4j
@Service
public class RuleEngineServiceAdapter implements RuleEngineServicePort {

    @Resource
    private RuleEngineService ruleEngineService;

    @Override
    public Object executeRule(String ruleSetCode, Map<String, Object> variables, String tenantId) {
        return ruleEngineService.executeRule(ruleSetCode, variables, tenantId);
    }

    @Override
    public Object validateRule(String ruleSetCode, Map<String, Object> variables, String tenantId) {
        return ruleEngineService.validateRule(ruleSetCode, variables, tenantId);
    }
}
