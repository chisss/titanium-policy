package com.titanium.policy.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.request.ConvertToInsuranceRequest;
import com.titanium.policy.api.response.InsuranceResponse;

/**
 * 投保单聚合对外契约（Feign）
 * <p>
 * 命名主键为聚合根 {@code Insurance}（投保单），仅承载投保单聚合的远程调用；正式保单
 * {@code Policy}、投保意向单 {@code Proposal} 各自独立契约。契约路径遵从内部服务远程
 * 调用规约 {@code /api/v1/insurances}，由 web 层 {@code InsuranceApiProvider} 实现，路径不得篡改。
 * </p>
 * <p>
 * 同域多个 {@code @FeignClient} 的 {@code name} 相同，必须各配唯一 {@code contextId}，
 * 否则 Spring 启动报「Multiple @FeignClient with the same name」Bean 冲突。
 * </p>
 */
@FeignClient(name = "titanium-policy", contextId = "insuranceApi", path = "/api/v1/insurances")
public interface InsuranceApi {

    /**
     * 从投保意向单转投保单（跨服务发起）
     *
     * @param dto 转换 DTO
     * @param tenantId 租户ID
     * @return 投保单ID
     */
    @PostMapping("/from-proposal")
    ApiResponse<String> convertFromProposal(@RequestBody ConvertToInsuranceRequest dto,
                                            @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 获取投保单详情（跨域集成用）
     *
     * @param insuranceId 投保单ID
     * @param tenantId 租户ID
     * @return 投保单详情，不存在时 code=404
     */
    @GetMapping("/{insuranceId}")
    ApiResponse<InsuranceResponse> getInsurance(@PathVariable("insuranceId") String insuranceId,
                                           @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 提交核保
     *
     * @param insuranceId 投保单ID
     * @param tenantId 租户ID
     * @return 空响应
     */
    @PutMapping("/{insuranceId}/underwriting")
    ApiResponse<Void> submitUnderwriting(@PathVariable("insuranceId") String insuranceId,
                                         @RequestHeader("X-Tenant-Id") String tenantId);
}
