package com.titanium.policy.application.service;

import com.titanium.ruleengine.api.RuleEngineApi;
import com.titanium.ruleengine.api.dto.RuleExecutionResultDTO;
import com.titanium.ruleengine.api.dto.ValidationResultDTO;
import com.titanium.ruleengine.api.response.ApiResponse;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 规则引擎服务客户端
 * 用于调用规则引擎的API
 */
@Slf4j
@Service
public class RuleEngineService {

    @Resource
    private RuleEngineApi ruleEngineApi;

    /**
     * 执行规则
     */
    public RuleExecutionResultDTO executeRule(String ruleSetCode, Map<String, Object> variables, String tenantId) {
        log.info("执行规则, ruleSetCode={}, tenantId={}", ruleSetCode, tenantId);
        ApiResponse<RuleExecutionResultDTO> response = ruleEngineApi.execute(ruleSetCode, variables, tenantId);
        if (response.isSuccess()) {
            return response.getData();
        } else {
            log.error("执行规则失败, ruleSetCode={}, error={}", ruleSetCode, response.getMessage());
            throw new RuntimeException("执行规则失败: " + response.getMessage());
        }
    }

    /**
     * 验证规则
     */
    public ValidationResultDTO validateRule(String ruleSetCode, Map<String, Object> variables, String tenantId) {
        log.info("验证规则, ruleSetCode={}, tenantId={}", ruleSetCode, tenantId);
        ApiResponse<ValidationResultDTO> response = ruleEngineApi.validate(ruleSetCode, variables, tenantId);
        if (response.isSuccess()) {
            return response.getData();
        } else {
            log.error("验证规则失败, ruleSetCode={}, error={}", ruleSetCode, response.getMessage());
            throw new RuntimeException("验证规则失败: " + response.getMessage());
        }
    }
}