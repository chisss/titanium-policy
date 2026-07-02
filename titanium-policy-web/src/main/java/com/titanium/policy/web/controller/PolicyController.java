package com.titanium.policy.web.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.policy.api.PolicyApi;
import com.titanium.policy.api.dto.AmountDTO;
import com.titanium.policy.api.dto.CreatePolicyDTO;
import com.titanium.policy.api.dto.PolicyDTO;
import com.titanium.policy.api.response.ApiResponse;
import com.titanium.policy.application.command.PolicyApplicationService;
import com.titanium.policy.application.query.PolicyAppQueryService;
import com.titanium.policy.query.result.PolicyQueryResult;
import com.titanium.policy.valueobject.IssuanceRequest;
import com.titanium.policy.valueobject.IssuanceResult;

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
        // 命令构造下沉应用层，表现层不依赖领域命令
        String policyId = policyApplicationService.createPolicy(createPolicyDTO);
        return ApiResponse.success(policyId);
    }

    @Override
    public ApiResponse<String> createPolicyDirectly(CreatePolicyDTO createPolicyDTO) {
        // 命令构造下沉应用层，表现层不依赖领域命令
        String policyId = policyApplicationService.createPolicyDirectly(createPolicyDTO);
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
        // 走读模型（QueryGateway → PolicyView），命中则组装为对外 PolicyDTO
        return policyAppQueryService.findById(policyId, tenantId)
                .map(result -> ApiResponse.success(toDTO(result)))
                .orElseGet(() -> ApiResponse.success(null));
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
        // 注：PolicyApi 的 command 为 Object 且未透传 reason/operator/tenantId（既有契约缺陷），暂以 null 占位；
        // 命令构造下沉应用层，表现层不依赖领域命令。待 API 契约补齐结构化入参后完善。
        policyApplicationService.suspendPolicy(policyId, null, null, null);
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> resumePolicy(String policyId, Object command) {
        policyApplicationService.resumePolicy(policyId, null, null, null);
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> terminatePolicy(String policyId, Object command) {
        policyApplicationService.terminatePolicy(policyId, null, null, null, null);
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> cancelPolicy(String policyId, Object command) {
        policyApplicationService.cancelPolicy(policyId, null, null, null);
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<List<PolicyDTO>> getPoliciesByCustomerId(String customerId) {
        // PolicyApi 未透传 tenantId，而读模型为多租户查询（须带 tenantId），无法在此安全执行；
        // 待 PolicyApi 补齐 tenantId 参数后接通 policyAppQueryService.findByCustomerId
        return ApiResponse.success(null);
    }

    @Override
    public ApiResponse<List<PolicyDTO>> getPoliciesByStatus(String status) {
        // 读侧 PolicyQueryHandler 暂未暴露按状态查询，待补充对应 Query 后接通
        return ApiResponse.success(null);
    }

    @Override
    public ApiResponse<List<PolicyDTO>> getAllPolicies() {
        // 读侧暂未提供全量查询（生产场景应分页），待补充分页 Query 后接通
        return ApiResponse.success(null);
    }

    /**
     * 读模型查询结果 → 对外保单 DTO 的表现层组装
     * <p>
     * Controller 层的展示组装（读模型结果 → API 契约 DTO），非领域实体跨层转换。
     * </p>
     *
     * @param result 读模型查询结果
     * @return 对外保单 DTO
     */
    private PolicyDTO toDTO(PolicyQueryResult result) {
        PolicyDTO dto = new PolicyDTO();
        dto.setPolicyId(result.getPolicyId());
        dto.setPolicyNo(result.getPolicyNo());
        dto.setCustomerId(result.getPolicyHolderId());
        dto.setProductId(result.getProductCode());
        dto.setEffectiveDate(result.getEffectiveDate());
        dto.setExpiryDate(result.getExpiryDate());
        dto.setStatus(result.getStatus());
        dto.setCreatedAt(result.getCreateTime());
        dto.setUpdatedAt(result.getUpdateTime());
        dto.setTenantId(result.getTenantId());
        if (result.getPremium() != null) {
            AmountDTO premium = new AmountDTO();
            premium.setValue(BigDecimal.valueOf(result.getPremium()));
            premium.setCurrency(result.getCurrency() != null ? result.getCurrency().name() : null);
            dto.setPremium(premium);
        }
        return dto;
    }
}
