package com.titanium.policy.web.provider;

import org.springframework.web.bind.annotation.RestController;

import com.titanium.policy.api.InsuranceApi;
import com.titanium.policy.api.dto.ConvertToInsuranceDTO;
import com.titanium.policy.api.dto.InsuranceDTO;
import com.titanium.policy.api.response.ApiResponse;
import com.titanium.policy.application.command.InsuranceApplicationService;
import com.titanium.policy.application.query.InsuranceAppQueryService;
import com.titanium.policy.command.ConvertProposalToInsuranceCommand;
import com.titanium.policy.web.mapper.InsuranceWebMapper;

import lombok.RequiredArgsConstructor;

/**
 * 投保单契约实现（Provider）
 * <p>
 * 承接 {@link InsuranceApi} Feign 契约，面向其它微服务的远程调用。路径由 {@link InsuranceApi} 的
 * {@code @RequestMapping("/api/v1/insurances")} 唯一定义，本类通过 {@code implements} 继承，
 * <b>不重复标注、不篡改</b>。职责仅为协议转换（DTO → 领域命令）+ 调用应用层门面，零业务逻辑。
 * 与面向后台/端上的 {@code InsuranceController} 平行收敛到同一 {@link InsuranceApplicationService}。
 * </p>
 */
@RestController
@RequiredArgsConstructor
public class InsuranceApiProvider implements InsuranceApi {

    private final InsuranceApplicationService insuranceApplicationService;

    private final InsuranceAppQueryService    insuranceAppQueryService;

    private final InsuranceWebMapper          insuranceWebMapper;

    @Override
    public ApiResponse<String> convertFromProposal(ConvertToInsuranceDTO dto, String tenantId) {
        // 协议转换：远程 DTO → 领域命令，收敛到同一应用层门面
        ConvertProposalToInsuranceCommand command = insuranceWebMapper.toCommand(dto, tenantId);
        String insuranceId = insuranceApplicationService.convertFromProposal(command);
        return ApiResponse.success(insuranceId);
    }

    @Override
    public ApiResponse<InsuranceDTO> getInsurance(String insuranceId, String tenantId) {
        // 走读模型（QueryGateway → InsuranceView），命中组装为对外 DTO，未命中返回 404 码
        return insuranceAppQueryService.findById(insuranceId, tenantId)
                .map(insuranceWebMapper::toDTO)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error(404, "投保单不存在: " + insuranceId));
    }

    @Override
    public ApiResponse<Void> submitUnderwriting(String insuranceId, String tenantId) {
        insuranceApplicationService.submitUnderwriting(insuranceId, tenantId);
        return ApiResponse.success();
    }
}
