package com.titanium.policy.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import com.titanium.policy.api.request.CreateProposalRequest;
import com.titanium.policy.api.response.ApiResponse;
import com.titanium.policy.api.response.ProposalResponse;

/**
 * 投保意向单聚合对外契约（Feign）
 * <p>
 * 命名主键为聚合根 {@code Proposal}，仅承载投保意向单聚合的远程调用；正式保单 {@code Policy}、
 * 投保单 {@code Insurance} 各自独立契约。契约路径遵从内部服务远程调用规约 {@code /api/v1/proposals}，
 * 由 web 层 {@code ProposalApiProvider} 实现，路径不得篡改。
 * </p>
 * <p>
 * 同域多个 {@code @FeignClient} 的 {@code name} 相同，必须各配唯一 {@code contextId}，
 * 否则 Spring 启动报「Multiple @FeignClient with the same name」Bean 冲突。
 * </p>
 */
@FeignClient(name = "titanium-policy", contextId = "proposalApi")
@RequestMapping("/api/v1/proposals")
public interface ProposalApi {

    /**
     * 创建投保意向单
     *
     * @param dto 创建投保意向单 DTO
     * @param tenantId 租户ID
     * @return 意向单ID
     */
    @PostMapping
    ApiResponse<String> createProposal(@RequestBody CreateProposalRequest dto,
                                       @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 获取投保意向单详情（跨域集成用）
     *
     * @param proposalId 意向单ID
     * @param tenantId 租户ID
     * @return 意向单详情，不存在时 code=404
     */
    @GetMapping("/{proposalId}")
    ApiResponse<ProposalResponse> getProposal(@PathVariable("proposalId") String proposalId,
                                         @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 提交投保意向单
     *
     * @param proposalId 意向单ID
     * @param tenantId 租户ID
     * @return 空响应
     */
    @PutMapping("/{proposalId}/submit")
    ApiResponse<Void> submitProposal(@PathVariable("proposalId") String proposalId,
                                     @RequestHeader("X-Tenant-Id") String tenantId);
}
