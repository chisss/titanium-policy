package com.titanium.policy.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.request.policy.DisbursePolicyLoanRequest;
import com.titanium.policy.api.request.policy.RepayPolicyLoanRequest;
import com.titanium.policy.api.response.policy.DisbursePolicyLoanResponse;
import com.titanium.policy.api.response.policy.RepayPolicyLoanResponse;

import jakarta.validation.Valid;

/**
 * 保单贷款远程契约（g04-03 放款 / g04-04 还款）。
 * <p>
 * 面向保全域放款编排器：保全域完成额度与策略取数后，以本契约把放款资金事实写入保单域贷款
 * 账户聚合。与 {@link PolicyApi} 同域同 {@code name}，故须配唯一 {@code contextId}
 * （与 {@link PolicyCashValueApi} 同一约定）。
 * </p>
 * <p>
 * 🔴 失败关闭：保单不存在 → {@code POLICY_NOT_EXIST}、保单未生效 →
 * {@code POLICY_NOT_EFFECTIVE}、金额/利率非法或重复放款 → {@code POLICY_RULE_VIOLATION}，
 * 一律业务码非成功且 {@code data} 为空，绝不部分入账。
 * </p>
 */
@FeignClient(name = "titanium-policy", contextId = "policyLoanApi", path = "/api/v1/loans")
public interface PolicyLoanApi {

    /**
     * 保单贷款放款。
     *
     * @param request 放款请求（贷款业务号 + 保单号 + 本金/冻结利率/放款日期）
     * @return 放款回执；校验失败时业务码非成功、{@code data} 为空
     */
    @PostMapping("/disburse")
    ApiResponse<DisbursePolicyLoanResponse> disburse(@Valid @RequestBody DisbursePolicyLoanRequest request);

    /**
     * 保单贷款还款（部分还款 / 提前清偿 / 结清共用）。
     * <p>
     * 🔴 约束注解（{@code @Valid}）声明在本契约侧，实现类（Provider）<b>不得重复声明</b>——
     * 覆写方法重复声明参数约束配置会触发 {@code HV000151}（Spring 方法校验代理在真机装配期
     * 报错，单元测试不可见）。同 {@link #disburse}。
     * </p>
     *
     * @param request 还款请求（还款业务号 + 目标明细 + 还款额/还款日期；分配由保单域聚合现场计算）
     * @return 还款回执（含利息/本金拆分与还款后余额）；校验失败时业务码非成功、{@code data} 为空
     */
    @PostMapping("/repay")
    ApiResponse<RepayPolicyLoanResponse> repay(@Valid @RequestBody RepayPolicyLoanRequest request);
}
