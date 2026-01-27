package com.titanium.policy.api;

import com.titanium.policy.api.dto.CreatePolicyDTO;
import com.titanium.policy.api.dto.PolicyDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * @author wayne sun
 * @note: 保单API接口定义
 */
public interface PolicyApi {

    /**
     * 创建保单
     *
     * @param createPolicyDTO 创建保单请求
     * @return 保单ID
     */
    @PostMapping("/api/policies")
    ResponseEntity<String> createPolicy(@RequestBody CreatePolicyDTO createPolicyDTO);

    /**
     * 获取保单详情
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 保单详情
     */
    @GetMapping("/api/policies/{policyId}")
    ResponseEntity<PolicyDTO> getPolicy(@PathVariable String policyId,
                                        @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 激活保单
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 响应结果
     */
    @PutMapping("/api/policies/{policyId}/activate")
    ResponseEntity<Void> activatePolicy(@PathVariable String policyId,
                                        @RequestHeader("X-Tenant-Id") String tenantId);
}
