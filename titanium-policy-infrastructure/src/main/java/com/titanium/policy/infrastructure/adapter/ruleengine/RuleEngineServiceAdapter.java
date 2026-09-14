package com.titanium.policy.infrastructure.adapter.ruleengine;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.titanium.metadata.enums.BusinessDomainType;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.common.enums.RuleEngineDecision;
import com.titanium.policy.port.ruleengine.RuleEngineServicePort;
import com.titanium.ruleengine.api.RuleEngineApi;
import com.titanium.ruleengine.api.response.execution.RuleExecutionResultResponse;
import com.titanium.ruleengine.api.response.execution.ValidationResultResponse;

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

    /**
     * 上报规则引擎的业务域类型：本适配器由保单域驱动，其发起的每一次规则执行在业务上都归属保单域，
     * 属端口的固有属性而非调用方入参，故在此常量承载，不污染 {@link RuleEngineServicePort} 签名。
     */
    private static final String BUSINESS_TYPE = BusinessDomainType.POLICY.getCode();

    private final RuleEngineApi ruleEngineApi;

    @Override
    public RuleEngineDecision executeRule(String ruleSetCode, Map<String, Object> variables, String tenantId,
                                          String businessId) {
        log.info("执行规则, ruleSetCode={}, tenantId={}, businessId={}", ruleSetCode, tenantId, businessId);
        ApiResponse<RuleExecutionResultResponse> response = ruleEngineApi.execute(ruleSetCode, variables, tenantId,
                businessId, BUSINESS_TYPE);
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
            // G16 新增结论：加费/除外均属条件承保，不阻断出单风控门，按通过处理（承保条件参数由核保域消费）
            case SURCHARGE, EXCLUDE -> RuleEngineDecision.PASS;
        };
    }

    @Override
    public boolean validateRule(String ruleSetCode, Map<String, Object> variables, String tenantId,
                                String businessId) {
        log.info("验证规则, ruleSetCode={}, tenantId={}, businessId={}", ruleSetCode, tenantId, businessId);
        ApiResponse<ValidationResultResponse> response = ruleEngineApi.validate(ruleSetCode, variables, tenantId,
                businessId, BUSINESS_TYPE);
        if (response == null || !response.isSuccess() || response.getData() == null) {
            String message = response != null ? response.getMessage() : "无响应";
            log.error("验证规则失败, ruleSetCode={}, error={}", ruleSetCode, message);
            throw new IllegalStateException("验证规则失败: " + message);
        }
        return response.getData().isValid();
    }
}
