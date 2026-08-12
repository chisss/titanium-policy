package com.titanium.policy.web.provider;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.PolicyIssuanceApi;
import com.titanium.policy.api.request.SubmitIssuanceRequest;
import com.titanium.policy.api.response.IssuanceResponse;
import com.titanium.policy.application.command.PolicyIssuanceApplicationService;
import com.titanium.policy.valueobject.IssuanceRequest;
import com.titanium.policy.valueobject.IssuanceResult;
import com.titanium.policy.web.assembler.IssuanceRequestAssembler;

import lombok.RequiredArgsConstructor;

/**
 * 出单流程契约实现（Provider）
 * <p>
 * 承接 {@link PolicyIssuanceApi} Feign 契约，面向其它微服务与合作方网关的远程调用。路径由契约的
 * {@code @FeignClient(path="/api/v1/issuances")} 唯一定义，本类通过 {@code implements} 继承，
 * <b>不重复标注、不篡改</b>。职责仅为协议转换（契约 Request → 领域出单请求 / 出单结果 → 契约
 * Response）+ 调用应用层门面，零业务逻辑。
 * </p>
 * <p>
 * 与面向后台/端上的 {@code PolicyIssuanceController} 平行收敛到同一
 * {@link PolicyIssuanceApplicationService}，二者互不依赖。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/issuances")
@RequiredArgsConstructor
public class PolicyIssuanceApiProvider implements PolicyIssuanceApi {

    private final PolicyIssuanceApplicationService policyIssuanceApplicationService;
    private final IssuanceRequestAssembler         issuanceRequestAssembler;

    @Override
    public ApiResponse<IssuanceResponse> submitIssuance(SubmitIssuanceRequest request, String tenantId) {
        IssuanceRequest domainRequest = issuanceRequestAssembler.toDomainRequest(request, tenantId);
        IssuanceResult result = policyIssuanceApplicationService.submitIssuance(domainRequest);
        IssuanceResponse response = issuanceRequestAssembler.toResponse(result);
        // 受理失败（要素校验/风控/模式不支持）以业务码返回，非 HTTP 错误——业务码 ≠ HTTP 状态码
        return result.success()
                ? ApiResponse.success(response)
                : ApiResponse.error(PolicyErrorCode.POLICY_CREATE_FAILED, result.rejectReason());
    }

    @Override
    public ApiResponse<IssuanceResponse> getIssuanceProgress(String bizNo, String tenantId) {
        return policyIssuanceApplicationService.getIssuanceProgress(bizNo, tenantId)
                .map(issuanceRequestAssembler::toResponse)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error(PolicyErrorCode.POLICY_NOT_EXIST, "出单流水号不存在: " + bizNo));
    }
}
