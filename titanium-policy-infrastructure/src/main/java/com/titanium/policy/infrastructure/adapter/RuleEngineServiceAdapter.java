package com.titanium.policy.infrastructure.adapter;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.titanium.policy.port.RuleEngineServicePort;
import com.titanium.ruleengine.api.RuleEngineApi;
import com.titanium.ruleengine.api.response.ApiResponse;
import com.titanium.ruleengine.api.response.RuleExecutionResultResponse;
import com.titanium.ruleengine.api.response.ValidationResultResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 规则引擎服务适配器
 * <p>
 * {@link RuleEngineServicePort} 的基础设施实现，直接调用规则引擎域 {@link RuleEngineApi}（Feign）并解包
 * {@link ApiResponse}。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleEngineServiceAdapter implements RuleEngineServicePort {

    private final RuleEngineApi ruleEngineApi;

    @Override
    public Object executeRule(String ruleSetCode, Map<String, Object> variables, String tenantId) {
        log.info("执行规则, ruleSetCode={}, tenantId={}", ruleSetCode, tenantId);
        ApiResponse<RuleExecutionResultResponse> response = ruleEngineApi.execute(ruleSetCode, variables, tenantId);
        if (response.isSuccess()) {
            return response.getData();
        }
        log.error("执行规则失败, ruleSetCode={}, error={}", ruleSetCode, response.getMessage());
        throw new RuntimeException("执行规则失败: " + response.getMessage());
    }

    @Override
    public Object validateRule(String ruleSetCode, Map<String, Object> variables, String tenantId) {
        log.info("验证规则, ruleSetCode={}, tenantId={}", ruleSetCode, tenantId);
        ApiResponse<ValidationResultResponse> response = ruleEngineApi.validate(ruleSetCode, variables, tenantId);
        if (response.isSuccess()) {
            return response.getData();
        }
        log.error("验证规则失败, ruleSetCode={}, error={}", ruleSetCode, response.getMessage());
        throw new RuntimeException("验证规则失败: " + response.getMessage());
    }
}
