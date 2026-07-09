package com.titanium.policy.web.provider;

import org.springframework.web.bind.annotation.RestController;

import com.titanium.policy.api.ProposalApi;
import com.titanium.policy.api.dto.CreateProposalDTO;
import com.titanium.policy.api.dto.ProposalDTO;
import com.titanium.policy.api.response.ApiResponse;
import com.titanium.policy.application.command.ProposalApplicationService;
import com.titanium.policy.application.query.ProposalAppQueryService;
import com.titanium.policy.command.CreateProposalCommand;
import com.titanium.policy.web.mapper.ProposalWebMapper;

import lombok.RequiredArgsConstructor;

/**
 * 投保意向单契约实现（Provider）
 * <p>
 * 承接 {@link ProposalApi} Feign 契约，面向其它微服务的远程调用。路径由 {@link ProposalApi} 的
 * {@code @RequestMapping("/api/v1/proposals")} 唯一定义，本类通过 {@code implements} 继承，
 * <b>不重复标注、不篡改</b>。职责仅为协议转换（DTO → 领域命令）+ 调用应用层门面，零业务逻辑。
 * 与面向后台/端上的 {@code ProposalController} 平行收敛到同一 {@link ProposalApplicationService}。
 * </p>
 */
@RestController
@RequiredArgsConstructor
public class ProposalApiProvider implements ProposalApi {

    private final ProposalApplicationService proposalApplicationService;

    private final ProposalAppQueryService    proposalAppQueryService;

    private final ProposalWebMapper          proposalWebMapper;

    @Override
    public ApiResponse<String> createProposal(CreateProposalDTO dto, String tenantId) {
        // 协议转换：远程 DTO → 领域命令，收敛到同一应用层门面
        CreateProposalCommand command = proposalWebMapper.toCommand(dto, tenantId);
        String proposalId = proposalApplicationService.createProposal(command);
        return ApiResponse.success(proposalId);
    }

    @Override
    public ApiResponse<ProposalDTO> getProposal(String proposalId, String tenantId) {
        // 走读模型（QueryGateway → ProposalView），命中组装为对外 DTO，未命中返回 404 码
        return proposalAppQueryService.findById(proposalId, tenantId)
                .map(proposalWebMapper::toDTO)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error(404, "投保意向单不存在: " + proposalId));
    }

    @Override
    public ApiResponse<Void> submitProposal(String proposalId, String tenantId) {
        proposalApplicationService.submitProposal(proposalId, "提交投保意向单", tenantId);
        return ApiResponse.success();
    }
}
