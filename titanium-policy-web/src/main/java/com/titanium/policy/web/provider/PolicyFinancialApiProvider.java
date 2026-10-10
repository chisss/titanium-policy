package com.titanium.policy.web.provider;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.common.context.RequestContextHolder;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.PolicyFinancialApi;
import com.titanium.policy.api.response.policy.PolicyFinancialResponse;
import com.titanium.policy.application.query.PolicyFinancialSnapshotQueryAppService;
import com.titanium.policy.valueobject.financial.PolicyFinancialSnapshot;

import lombok.RequiredArgsConstructor;

/**
 * 保单财务快照契约实现（Provider，g04-02）。
 * <p>
 * 路径由 {@link PolicyFinancialApi} 的 {@code @RequestMapping("/api/v1/policies")} 唯一定义，
 * 本类通过 {@code implements} 继承，<b>不重复标注、不篡改</b>。职责仅为协议转换 + 调用应用层
 * 读门面，零业务逻辑。
 * </p>
 * <p>
 * 🔴 失败关闭：取数编排服务缺数据时抛出携带错误码的领域异常
 * （{@code POLICY_NOT_EXIST} / {@code POLICY_FINANCIAL_DATA_MISSING}），由
 * {@code PolicyExceptionHandler} 统一转为业务码失败响应（{@code data} 为空），
 * 本类不兜底、不伪造金额。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
public class PolicyFinancialApiProvider implements PolicyFinancialApi {

    private final PolicyFinancialSnapshotQueryAppService policyFinancialSnapshotQueryAppService;

    @Override
    public ApiResponse<PolicyFinancialResponse> getFinancialSnapshot(String policyId) {
        PolicyFinancialSnapshot snapshot = policyFinancialSnapshotQueryAppService
                .query(RequestContextHolder.requireTenantId(), policyId);
        return ApiResponse.success(new PolicyFinancialResponse(
                policyId,
                snapshot.cashValue(),
                snapshot.sumInsured(),
                snapshot.mainSegmentId(),
                snapshot.loanOutstandingPrincipal(),
                snapshot.loanOutstandingInterest(),
                snapshot.arrearsAmount(),
                snapshot.dividendBalance(),
                snapshot.accountValue(),
                snapshot.effectiveDate(),
                snapshot.paidInstallments(),
                snapshot.issuanceBizNo(),
                snapshot.annualPremium(),
                snapshot.coverageEndDate(),
                snapshot.premiumPaidUp()));
    }
}
