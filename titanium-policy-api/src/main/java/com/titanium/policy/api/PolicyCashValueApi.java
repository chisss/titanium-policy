package com.titanium.policy.api;

import java.time.LocalDate;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.response.policy.PolicyCashValueResponse;

/**
 * 保单现金价值只读远程契约。
 * <p>
 * 与 {@link PolicyApi} 同域同 {@code name}，故须配唯一 {@code contextId}，否则 Spring 启动报
 * 「Multiple @FeignClient with the same name」。按功能切分独立契约，避免 {@code PolicyApi} 承载
 * 过多职责。
 * </p>
 * <p>
 * 调用方（计费域垫缴判定等）只需保单号：保单域内部完成「保单要素 → 产品域退保价值策略」的问询，
 * 现金价值口径由保单域统一封装，消费方不得自行推导。
 * </p>
 */
@FeignClient(name = "titanium-policy", contextId = "policyCashValueApi", path = "/api/v1/policies")
public interface PolicyCashValueApi {

    /**
     * 查保单在指定估值日的现金价值（只读，零副作用）。
     *
     * @param policyId 保单ID
     * @param valuationDate 估值日（为空取当日）
     * @param tenantId 租户ID
     * @return 现金价值；保单不存在、要素缺失或产品域无适用策略时业务码非成功、{@code data} 为空
     */
    @GetMapping("/{policyId}/cash-value")
    ApiResponse<PolicyCashValueResponse> getCashValue(@PathVariable("policyId") String policyId,
            @RequestParam(value = "valuationDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate valuationDate,
            @RequestHeader("X-Tenant-ID") String tenantId);
}
