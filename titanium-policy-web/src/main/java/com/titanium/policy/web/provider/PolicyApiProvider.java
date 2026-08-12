package com.titanium.policy.web.provider;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.PolicyApi;
import com.titanium.policy.api.request.AccountValueWriteBackRequest;
import com.titanium.policy.api.request.CreatePolicyRequest;
import com.titanium.policy.api.response.PolicyResponse;
import com.titanium.policy.api.response.PolicyStatusResponse;
import com.titanium.policy.application.command.PolicyApplicationService;
import com.titanium.policy.application.query.PolicyAppQueryService;
import com.titanium.policy.command.CreatePolicyCommand;
import com.titanium.policy.command.CreatePolicyDirectlyCommand;
import com.titanium.policy.query.query.FindPolicyByIdQuery;
import com.titanium.policy.web.mapper.PolicyWebMapper;

import lombok.RequiredArgsConstructor;

/**
 * 保单契约实现（Provider）
 * <p>
 * 承接 {@link PolicyApi} Feign 契约，面向其它微服务的远程调用。路径由 {@link PolicyApi} 的
 * {@code @RequestMapping("/api/v1/policies")} 唯一定义，本类通过 {@code implements} 继承，
 * <b>不重复标注、不篡改</b>。职责仅为协议转换（DTO ⇄ 用例 Input）+ 调用应用层门面，零业务逻辑。
 * 与面向后台/端上的 {@code PolicyController} 平行收敛到同一 {@link PolicyApplicationService}。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
public class PolicyApiProvider implements PolicyApi {

    private final PolicyApplicationService policyApplicationService;

    private final PolicyAppQueryService    policyAppQueryService;

    private final PolicyWebMapper          policyWebMapper;

    @Override
    public ApiResponse<String> createPolicy(CreatePolicyRequest dto, String tenantId) {
        // 协议转换：远程 DTO → 领域命令，收敛到同一应用层门面
        CreatePolicyCommand command = policyWebMapper.toCommand(dto, tenantId);
        String policyId = policyApplicationService.createPolicy(command);
        return ApiResponse.success(policyId);
    }

    @Override
    public ApiResponse<String> createPolicyDirectly(CreatePolicyRequest dto, String tenantId) {
        // 一步出单：DTO → 一步出单命令，收敛到 application 门面
        CreatePolicyDirectlyCommand command = policyWebMapper.toDirectCommand(dto, tenantId);
        String policyId = policyApplicationService.createPolicyDirectly(command);
        return ApiResponse.success(policyId);
    }

    @Override
    public ApiResponse<PolicyResponse> getPolicy(String policyId, String tenantId) {
        // 读：构造 FindPolicyByIdQuery 交读门面派发（QueryGateway → PolicyView），未命中返回 404 码
        return policyAppQueryService.findById(new FindPolicyByIdQuery(policyId, tenantId))
                .map(policyWebMapper::toResponse)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error(PolicyErrorCode.POLICY_NOT_EXIST, "保单不存在: " + policyId));
    }

    @Override
    public ApiResponse<PolicyStatusResponse> getPolicyStatus(String policyId, String tenantId) {
        return policyAppQueryService.findById(new FindPolicyByIdQuery(policyId, tenantId))
                .map(policyWebMapper::toStatusResponse)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error(PolicyErrorCode.POLICY_NOT_EXIST, "保单不存在: " + policyId));
    }

    @Override
    public ApiResponse<Void> issuePolicy(String policyId, String operatorId, String tenantId) {
        policyApplicationService.issuePolicy(policyId, operatorId, tenantId);
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> activatePolicy(String policyId, String tenantId) {
        policyApplicationService.activatePolicy(policyId, tenantId);
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> writeBackAccountValue(String policyId, AccountValueWriteBackRequest dto, String tenantId) {
        // 协议转换：投资域回写请求 → 更新账户价值命令，收敛到 application 门面
        policyApplicationService.updateAccountValue(policyId, dto.getAccountId(), dto.getAccountValue(),
                dto.getCurrency(), tenantId);
        return ApiResponse.success();
    }
}
