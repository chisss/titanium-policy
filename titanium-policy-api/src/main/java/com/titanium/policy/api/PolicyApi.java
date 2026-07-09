package com.titanium.policy.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import com.titanium.policy.api.dto.CreatePolicyDTO;
import com.titanium.policy.api.dto.PolicyDTO;
import com.titanium.policy.api.dto.PolicyStatusDTO;
import com.titanium.policy.api.response.ApiResponse;

/**
 * 保单聚合对外契约（Feign）
 * <p>
 * 命名主键为聚合根 {@code Policy}（非 policy 域），仅承载正式保单聚合的远程调用；投保单
 * {@code Insurance}、投保意向单 {@code Proposal} 各自独立契约。契约路径遵从内部服务远程
 * 调用规约 {@code /api/v1/policies}，由 web 层 {@code PolicyApiProvider} 实现，路径不得篡改。
 * </p>
 * <p>
 * 同域多个 {@code @FeignClient} 的 {@code name} 相同，必须各配唯一 {@code contextId}，
 * 否则 Spring 启动报「Multiple @FeignClient with the same name」Bean 冲突。
 * </p>
 */
@FeignClient(name = "titanium-policy", contextId = "policyApi")
@RequestMapping("/api/v1/policies")
public interface PolicyApi {

    /**
     * 创建保单（从投保单创建）
     *
     * @param dto 创建保单 DTO
     * @param tenantId 租户ID
     * @return 保单ID
     */
    @PostMapping
    ApiResponse<String> createPolicy(@RequestBody CreatePolicyDTO dto,
                                     @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 一步出单（直接创建并签发）
     *
     * @param dto 创建保单 DTO
     * @param tenantId 租户ID
     * @return 保单ID
     */
    @PostMapping("/direct")
    ApiResponse<String> createPolicyDirectly(@RequestBody CreatePolicyDTO dto,
                                             @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 获取保单详情（跨域集成用）
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 保单详情，不存在时 code=404
     */
    @GetMapping("/{policyId}")
    ApiResponse<PolicyDTO> getPolicy(@PathVariable("policyId") String policyId,
                                     @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 查询保单状态（跨域集成用，返回保单原生状态码）
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 保单状态，不存在时 code=404
     */
    @GetMapping("/{policyId}/status")
    ApiResponse<PolicyStatusDTO> getPolicyStatus(@PathVariable("policyId") String policyId,
                                                 @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 签发保单
     *
     * @param policyId 保单ID
     * @param operatorId 操作人ID
     * @param tenantId 租户ID
     * @return 空响应
     */
    @PutMapping("/{policyId}/issue")
    ApiResponse<Void> issuePolicy(@PathVariable("policyId") String policyId,
                                  @RequestHeader("X-Operator-Id") String operatorId,
                                  @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 激活保单
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 空响应
     */
    @PutMapping("/{policyId}/activate")
    ApiResponse<Void> activatePolicy(@PathVariable("policyId") String policyId,
                                     @RequestHeader("X-Tenant-Id") String tenantId);
}
