package com.titanium.policy.infrastructure.adapter;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.common.enums.RuleEngineDecision;
import com.titanium.policy.port.RuleEngineServicePort;
import com.titanium.ruleengine.api.RuleEngineApi;
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
    public RuleEngineDecision executeRule(String ruleSetCode, Map<String, Object> variables, String tenantId) {
        log.info("执行规则, ruleSetCode={}, tenantId={}", ruleSetCode, tenantId);
        ApiResponse<RuleExecutionResultResponse> response = ruleEngineApi.execute(ruleSetCode, variables, tenantId);
        if (response == null || !response.isSuccess() || response.getData() == null) {
            String message = response != null ? response.getMessage() : "无响应";
            log.error("执行规则失败, ruleSetCode={}, error={}", ruleSetCode, message);
            throw new IllegalStateException("执行规则失败: " + message);
        }
        if (response.getData().getDecision() == null) {
            throw new IllegalStateException("执行规则失败: 裁决结果为空");
        }
        return switch (response.getData().getDecision()) {
            case PASS -> RuleEngineDecision.PASS;
            case REJECT -> RuleEngineDecision.REJECT;
            case REFER -> RuleEngineDecision.REFER;
        };
    }

    @Override
    public boolean validateRule(String ruleSetCode, Map<String, Object> variables, String tenantId) {
        log.info("验证规则, ruleSetCode={}, tenantId={}", ruleSetCode, tenantId);
        ApiResponse<ValidationResultResponse> response = ruleEngineApi.validate(ruleSetCode, variables, tenantId);
        if (response == null || !response.isSuccess() || response.getData() == null) {
            String message = response != null ? response.getMessage() : "无响应";
            log.error("验证规则失败, ruleSetCode={}, error={}", ruleSetCode, message);
            throw new IllegalStateException("验证规则失败: " + message);
        }
        return response.getData().isValid();
    }
}
