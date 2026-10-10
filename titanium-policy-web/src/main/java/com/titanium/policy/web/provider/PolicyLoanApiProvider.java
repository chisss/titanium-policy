package com.titanium.policy.web.provider;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.common.context.RequestContextHolder;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.PolicyLoanApi;
import com.titanium.policy.api.request.policy.DisbursePolicyLoanRequest;
import com.titanium.policy.api.request.policy.RepayPolicyLoanRequest;
import com.titanium.policy.api.response.policy.DisbursePolicyLoanResponse;
import com.titanium.policy.api.response.policy.RepayPolicyLoanResponse;
import com.titanium.policy.application.command.policy.PolicyLoanApplicationService;
import com.titanium.policy.command.DisbursePolicyLoanCommand;
import com.titanium.policy.command.RepayPolicyLoanCommand;
import com.titanium.policy.entity.loan.PolicyLoanEntry;
import com.titanium.policy.entity.loan.PolicyLoanRepaymentRecord;

import lombok.RequiredArgsConstructor;

/**
 * 保单贷款契约实现（Provider，g04-03）。
 * <p>
 * 路径由 {@link PolicyLoanApi} 的 {@code @RequestMapping("/api/v1/loans")} 唯一定义，
 * 本类通过 {@code implements} 继承，<b>不重复标注、不篡改</b>。职责仅为协议转换：取租户/操作人
 * 上下文、请求 → 命令直译、调用应用层写门面，零业务逻辑。
 * </p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
public class PolicyLoanApiProvider implements PolicyLoanApi {

    private final PolicyLoanApplicationService policyLoanApplicationService;

    @Override
    public ApiResponse<DisbursePolicyLoanResponse> disburse(DisbursePolicyLoanRequest request) {
        String tenantId = RequestContextHolder.requireTenantId();
        String operatorId = RequestContextHolder.requireOperatorId();
        PolicyLoanEntry entry = policyLoanApplicationService.disburse(DisbursePolicyLoanCommand.of(
                tenantId, request.policyId(), request.loanId(), request.loanAmount(),
                request.interestRate(), request.loanDate(), operatorId));
        // 回显放款明细事实：幂等重试时利率与放款日期以首次入账为准，调用方不得以重试载荷漂移
        return ApiResponse.success(new DisbursePolicyLoanResponse(
                entry.loanId(), request.policyId(), entry.loanAmount(),
                entry.interestRate(), entry.loanDate()));
    }

    @Override
    public ApiResponse<RepayPolicyLoanResponse> repay(RepayPolicyLoanRequest request) {
        String tenantId = RequestContextHolder.requireTenantId();
        String operatorId = RequestContextHolder.requireOperatorId();
        PolicyLoanEntry entry = policyLoanApplicationService.repay(RepayPolicyLoanCommand.of(
                tenantId, request.policyId(), request.loanId(), request.repaymentId(),
                request.repaymentAmount(), request.repaymentDate(), operatorId));
        // 回显本次还款的权威分配（幂等重试时回显首次入账的分配结果）；余额由聚合状态派生，
        // 与分配同源——调用方（保全域）不得复算
        PolicyLoanRepaymentRecord record = entry.findRepayment(request.repaymentId());
        return ApiResponse.success(new RepayPolicyLoanResponse(
                record.repaymentId(), request.policyId(), entry.loanId(), record.repaymentAmount(),
                record.interestPortion(), record.principalPortion(), record.accruedInterest(),
                entry.outstandingPrincipal(), entry.unpaidInterest(), entry.settled(),
                record.repaymentDate()));
    }
}
