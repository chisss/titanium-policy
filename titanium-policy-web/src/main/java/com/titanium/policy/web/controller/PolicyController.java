package com.titanium.policy.web.controller;

import com.titanium.policy.api.PolicyApi;
import com.titanium.policy.api.dto.CreatePolicyDTO;
import com.titanium.policy.api.dto.PolicyDTO;
import com.titanium.policy.api.response.ApiResponse;
import com.titanium.policy.application.command.PolicyApplicationService;
import com.titanium.policy.application.query.PolicyAppQueryService;
import com.titanium.policy.command.*;
import com.titanium.policy.service.IssuanceRequest;
import com.titanium.policy.service.IssuanceResult;
import com.titanium.policy.valueobject.IssuanceProcessConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 保单控制器
 * 实现PolicyApi接口，为管理后台提供访问
 */
@RestController
@RequestMapping("/web/policies")
public class PolicyController implements PolicyApi {

    @Autowired
    private PolicyApplicationService policyApplicationService;

    @Autowired
    private PolicyAppQueryService policyAppQueryService;

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
        IssuanceProcessConfig config = IssuanceProcessConfig.oneStep(((IssuanceRequest) request).productCode());
        IssuanceResult result = policyApplicationService.issueByConfig(config, (IssuanceRequest) request);
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
