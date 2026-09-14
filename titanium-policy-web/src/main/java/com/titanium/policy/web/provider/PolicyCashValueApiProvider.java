package com.titanium.policy.web.provider;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.PolicyCashValueApi;
import com.titanium.policy.api.response.policy.PolicyCashValueResponse;
import com.titanium.policy.application.query.PolicyCashValueQueryAppService;
import com.titanium.policy.valueobject.product.PolicyCashValue;

import lombok.RequiredArgsConstructor;

/**
 * 保单现金价值契约实现（Provider）。
 * <p>
 * 路径由 {@link PolicyCashValueApi} 的 {@code @RequestMapping("/api/v1/policies")} 唯一定义，本类通过
 * {@code implements} 继承，<b>不重复标注、不篡改</b>。职责仅为协议转换 + 调用应用层读门面，零业务逻辑。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
public class PolicyCashValueApiProvider implements PolicyCashValueApi {

    private final PolicyCashValueQueryAppService policyCashValueQueryAppService;

    @Override
    public ApiResponse<PolicyCashValueResponse> getCashValue(String policyId, LocalDate valuationDate,
            String tenantId) {
        Optional<PolicyCashValue> cashValue = policyCashValueQueryAppService.query(tenantId, policyId, valuationDate);
        // 保单缺失、要素不全或产品域无适用策略：返回业务码失败（非 HTTP 错误），调用方据此降级
        return cashValue
                .map(value -> ApiResponse
                        .success(new PolicyCashValueResponse(policyId, value.productId(), value.policyYear(),
                                value.withinCoolingOff(), value.refundType(), value.cashValueRate(),
                                value.cashValue(), value.policyCode(), value.policyVersion(),
                                value.policyContentHash())))
                .orElseGet(() -> ApiResponse.error(PolicyErrorCode.POLICY_NOT_EXIST,
                        "保单 " + policyId + " 无可查的现金价值（保单不存在或产品未配置退保价值策略）"));
    }
}
