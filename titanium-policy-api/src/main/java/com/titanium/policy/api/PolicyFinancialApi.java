package com.titanium.policy.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.response.policy.PolicyFinancialResponse;

/**
 * 保单财务快照只读远程契约（g04-02）。
 * <p>
 * 与 {@link PolicyApi} 同域同 {@code name}，故须配唯一 {@code contextId}，否则 Spring 启动报
 * 「Multiple @FeignClient with the same name」（与 {@link PolicyCashValueApi} 同一约定）。
 * </p>
 * <p>
 * 调用方（保全域贷款/退费/减额缴清取数）只需保单号：保单域内部完成「事件流重建保单要素 →
 * 产品域现金价值 → 计费域欠费 → 贷款账户本息」的全部取数与组装，口径由保单域统一封装，
 * 消费方不得自行推导。🔴 取数失败关闭：保单不存在返回 {@code POLICY_NOT_EXIST}、
 * 财务要素缺失返回 {@code POLICY_FINANCIAL_DATA_MISSING}，绝不返回以 0 兜底的伪快照。
 * </p>
 */
@FeignClient(name = "titanium-policy", contextId = "policyFinancialApi", path = "/api/v1/policies")
public interface PolicyFinancialApi {

    /**
     * 查保单财务快照（只读，零副作用，写侧强一致）。
     *
     * @param policyId 保单ID
     * @return 财务快照；保单不存在、要素缺失或产品域无适用策略时业务码非成功、{@code data} 为空
     */
    @GetMapping("/{policyId}/financial-snapshot")
    ApiResponse<PolicyFinancialResponse> getFinancialSnapshot(@PathVariable String policyId);
}
