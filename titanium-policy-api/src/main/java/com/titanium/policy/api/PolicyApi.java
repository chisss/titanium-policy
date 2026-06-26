package com.titanium.policy.api;

import com.titanium.policy.api.dto.CreatePolicyDTO;
import com.titanium.policy.api.dto.PolicyDTO;
import com.titanium.policy.api.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * 保单API
 * 定义其他项目调用的Feign接口
 */
@FeignClient(name = "titanium-policy")
@RequestMapping("/api/policies")
public interface PolicyApi {
    /**
     * 创建保单
     */
    @PostMapping
    ApiResponse<String> createPolicy(@RequestBody CreatePolicyDTO createPolicyDTO);

    /**
     * 一步出单
     */
    @PostMapping("/direct")
    ApiResponse<String> createPolicyDirectly(@RequestBody CreatePolicyDTO createPolicyDTO);

    /**
     * 智能出单
     */
    @PostMapping("/issue")
    ApiResponse<Object> issueByConfig(@RequestBody Object request, @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 获取保单详情
     */
    @GetMapping("/{policyId}")
    ApiResponse<PolicyDTO> getPolicy(@PathVariable String policyId, @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 签发保单
     */
    @PutMapping("/{policyId}/issue")
    ApiResponse<Void> issuePolicy(@PathVariable String policyId, @RequestHeader("X-Operator-Id") String operatorId, @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 激活保单
     */
    @PutMapping("/{policyId}/activate")
    ApiResponse<Void> activatePolicy(@PathVariable String policyId, @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 暂停保单
     */
    @PutMapping("/{policyId}/suspend")
    ApiResponse<Void> suspendPolicy(@PathVariable String policyId, @RequestBody Object command);

    /**
     * 恢复保单
     */
    @PutMapping("/{policyId}/resume")
    ApiResponse<Void> resumePolicy(@PathVariable String policyId, @RequestBody Object command);

    /**
     * 终止保单
     */
    @PutMapping("/{policyId}/terminate")
    ApiResponse<Void> terminatePolicy(@PathVariable String policyId, @RequestBody Object command);

    /**
     * 取消保单
     */
    @PutMapping("/{policyId}/cancel")
    ApiResponse<Void> cancelPolicy(@PathVariable String policyId, @RequestBody Object command);

    /**
     * 根据客户ID查询保单列表
     */
    @GetMapping("/customer/{customerId}")
    ApiResponse<List<PolicyDTO>> getPoliciesByCustomerId(@PathVariable String customerId);

    /**
     * 根据状态查询保单列表
     */
    @GetMapping("/status/{status}")
    ApiResponse<List<PolicyDTO>> getPoliciesByStatus(@PathVariable String status);

    /**
     * 查询所有保单列表
     */
    @GetMapping
    ApiResponse<List<PolicyDTO>> getAllPolicies();
}

