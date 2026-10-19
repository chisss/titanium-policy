package com.titanium.policy.api;

import java.time.LocalDate;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.titanium.metadata.enums.Reserved;
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
 * 🔴 <b>跨域消费者已于 m27-04/PKG-04 归零</b>：计费域垫缴判定改为「本地计费要素投影 + 直连 product
 * 只读端点」，不再经本域中转（原链路即 billing→policy 同步依赖环的一侧）。本端点保留为保单域的
 * 对外只读能力，域内 {@code PolicyFinancialSnapshotQueryAppService} 复用同一应用服务；若后续确认
 * 无外部集成需求，可随契约一并删除。
 * </p>
 */
@FeignClient(name = "titanium-policy", contextId = "policyCashValueApi", path = "/api/v1/policies")
public interface PolicyCashValueApi {

    /**
     * 查保单在指定估值日的现金价值（只读，零副作用）。
     *
     * @param policyId 保单ID
     * @param valuationDate 估值日（为空取当日）
     * @return 现金价值；保单不存在、要素缺失或产品域无适用策略时业务码非成功、{@code data} 为空
     */
    @Reserved("m27-04 后跨域零消费者（billing 改直连 product）；保留为保单域对外只读能力，见契约登记表 §二")
    @GetMapping("/{policyId}/cash-value")
    ApiResponse<PolicyCashValueResponse> getCashValue(@PathVariable String policyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate valuationDate);
}
