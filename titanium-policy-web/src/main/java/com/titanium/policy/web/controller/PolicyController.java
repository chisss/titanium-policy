package com.titanium.policy.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.policy.api.PolicyApi;
import com.titanium.policy.api.dto.CreatePolicyDTO;
import com.titanium.policy.api.dto.PolicyDTO;
import com.titanium.policy.api.response.ApiResponse;
import com.titanium.policy.application.command.PolicyApplicationService;
import com.titanium.policy.application.query.PolicyAppQueryService;
import com.titanium.policy.command.CancelPolicyCommand;
import com.titanium.policy.command.CreatePolicyCommand;
import com.titanium.policy.command.CreatePolicyDirectlyCommand;
import com.titanium.policy.command.ResumePolicyCommand;
import com.titanium.policy.command.SuspendPolicyCommand;
import com.titanium.policy.command.TerminatePolicyCommand;
import com.titanium.policy.service.IssuanceRequest;
import com.titanium.policy.service.IssuanceResult;

import lombok.RequiredArgsConstructor;

/**
 * 保单控制器
 * 实现PolicyApi接口，为管理后台提供访问
 */
@RestController
@RequestMapping("/web/policies")
@RequiredArgsConstructor
public class PolicyController implements PolicyApi {

    private final PolicyApplicationService policyApplicationService;

    private final PolicyAppQueryService policyAppQueryService;

    @Override
    public ApiResponse<String> createPolicy(CreatePolicyDTO createPolicyDTO) {
        // 按 CreatePolicyCommand 真实组件构造，DTO 未承载的字段（投保单/形态/机构/保额/渠道）暂置 null
        CreatePolicyCommand command = new CreatePolicyCommand(
                createPolicyDTO.getPolicyId(),
                createPolicyDTO.getPolicyNumber(),
                null,
                null,
                null,
                createPolicyDTO.getCustomerId(),
                null,
                null,
                null,
                createPolicyDTO.getEffectiveDate(),
                createPolicyDTO.getExpiryDate(),
                null,
                createPolicyDTO.getTenantId());
        String policyId = policyApplicationService.createPolicy(command);
        return ApiResponse.success(policyId);
    }

    @Override
    public ApiResponse<String> createPolicyDirectly(CreatePolicyDTO createPolicyDTO) {
        // 按 CreatePolicyDirectlyCommand 真实组件构造，DTO 未承载字段暂置默认值
        CreatePolicyDirectlyCommand command = new CreatePolicyDirectlyCommand(
                createPolicyDTO.getPolicyId(),
                createPolicyDTO.getPolicyNumber(),
                createPolicyDTO.getProductId(),
                null,
                null,
                createPolicyDTO.getCustomerId(),
                0,
                null,
                createPolicyDTO.getEffectiveDate(),
                createPolicyDTO.getExpiryDate(),
                null,
                createPolicyDTO.getTenantId());
        String policyId = policyApplicationService.createPolicyDirectly(command);
        return ApiResponse.success(policyId);
    }

    @Override
    public ApiResponse<Object> issueByConfig(Object request, String tenantId) {
        // 出单模式由产品域配置决定，不再硬编码步数
        IssuanceResult result = policyApplicationService.issue((IssuanceRequest) request);
        return ApiResponse.success(result);
    }

    @Override
    public ApiResponse<PolicyDTO> getPolicy(String policyId, String tenantId) {
        // 这里需要转换为PolicyDTO
        return ApiResponse.success(null);
    }

    @Override
    public ApiResponse<Void> issuePolicy(String policyId, String operatorId, String tenantId) {
        policyApplicationService.issuePolicy(policyId, operatorId, tenantId);
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> activatePolicy(String policyId, String tenantId) {
        policyApplicationService.activatePolicy(policyId, tenantId);
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> suspendPolicy(String policyId, Object command) {
        policyApplicationService.suspendPolicy((SuspendPolicyCommand) command);
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> resumePolicy(String policyId, Object command) {
        policyApplicationService.resumePolicy((ResumePolicyCommand) command);
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> terminatePolicy(String policyId, Object command) {
        policyApplicationService.terminatePolicy((TerminatePolicyCommand) command);
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> cancelPolicy(String policyId, Object command) {
        policyApplicationService.cancelPolicy((CancelPolicyCommand) command);
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<List<PolicyDTO>> getPoliciesByCustomerId(String customerId) {
        // 这里需要实现查询逻辑
        return ApiResponse.success(null);
    }

    @Override
    public ApiResponse<List<PolicyDTO>> getPoliciesByStatus(String status) {
        // 这里需要实现查询逻辑
        return ApiResponse.success(null);
    }

    @Override
    public ApiResponse<List<PolicyDTO>> getAllPolicies() {
        // 这里需要实现查询逻辑
        return ApiResponse.success(null);
    }
}
