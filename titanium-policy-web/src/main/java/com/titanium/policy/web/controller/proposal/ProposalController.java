package com.titanium.policy.web.controller.proposal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.common.context.RequestContextHolder;
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.exception.DomainException;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.application.command.proposal.ProposalApplicationService;
import com.titanium.policy.application.query.ProposalAppQueryService;
import com.titanium.policy.command.CreateProposalCommand;
import com.titanium.policy.web.dto.issuance.CreateProposalDTO;
import com.titanium.policy.web.handler.PolicyExceptionHandler;
import com.titanium.policy.web.mapper.ProposalWebMapper;
import com.titanium.policy.web.response.proposal.ProposalVO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 投保意向单控制器（后台/端上 HTTP 入口）
 * <p>
 * 面向管理后台/端上，路径 {@code /web/v1/proposals}，<b>不 implements ProposalApi</b>（远程契约由
 * {@code ProposalApiProvider} 承接）。表现层经 {@link ProposalWebMapper} 把 Request 转成
 * CQRS 命令/查询： 写入口构造 {@link CreateProposalCommand} 交
 * {@link ProposalApplicationService}，读入口经 {@link ProposalAppQueryService}
 * 查读模型并转 VO。web 可依赖 command/query，但不碰聚合根。 与 {@code ProposalApiProvider}
 * 平行收敛到同一应用层门面。
 * </p>
 */
@RestController
@RequestMapping("/web/v1/proposals")
@RequiredArgsConstructor
public class ProposalController {

    private final ProposalApplicationService proposalApplicationService;

    private final ProposalAppQueryService    proposalAppQueryService;

    private final ProposalWebMapper          proposalWebMapper;

    /**
     * 创建投保意向单
     *
     * @param request 创建请求
     * @return 意向单ID
     */
    @PostMapping
    public ResponseEntity<ApiResponse<String>> createProposal(@Valid @RequestBody CreateProposalDTO request) {
        String tenantId = RequestContextHolder.requireTenantId();
        // 协议转换：HTTP Request → 领域命令，收敛到同一应用层门面
        CreateProposalCommand command = proposalWebMapper.toCommand(request, tenantId);
        String proposalId = proposalApplicationService.createProposal(command);
        // 保留 201：创建语义属 HTTP 传输层，不并入 ApiResponse.code
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(proposalId));
    }

    /**
     * 获取投保意向单详情
     * <p>
     * 🔴 未命中时抛 {@link DomainException} 而非返回空 body 的 404（R8-05），同
     * {@code PolicyController#getPolicy}：查不到既可能是「该意向单不存在」也可能是「租户错配」，
     * 空 body 让调用方无从分辨；两种成因仍合并表述（分开报会成为探测 id 是否存在的侧信道）。
     * </p>
     *
     * @param proposalId 投保意向单ID
     * @return 投保意向单详情
     */
    @GetMapping("/{proposalId}")
    public ApiResponse<ProposalVO> getProposal(@PathVariable String proposalId) {
        String tenantId = RequestContextHolder.requireTenantId();
        Optional<ProposalVO> vo = proposalAppQueryService.findById(proposalId, tenantId)
             .map(proposalWebMapper::toVO);
        return vo.map(ApiResponse::success).orElseThrow(
             () -> PolicyExceptionHandler.notFoundInTenant(PolicyErrorCode.PROPOSAL_NOT_EXIST, proposalId));
    }

    /**
     * 多条件分页查询投保意向单列表
     * <p>
     * 面向管理后台组合检索：意向单编号（模糊）/客户ID（精确）/险种编码（精确）/状态任意组合。
     * </p>
     *
     * @param proposalNo  意向单编号（可空）
     * @param customerId  客户ID（可空）
     * @param productCode 险种编码（可空）
     * @param status      意向单状态（可空）
     * @param page        页码（从0开始）
     * @param size        每页条数
     * @return 意向单列表
     */
    @GetMapping
    public ApiResponse<List<ProposalVO>> listProposals(
         @RequestParam(required = false) String proposalNo,
         @RequestParam(required = false) String customerId,
         @RequestParam(required = false) String productCode,
         @RequestParam(required = false) String status,
         @RequestParam(defaultValue = "0") int page,
         @RequestParam(defaultValue = "20") int size) {
        String tenantId = RequestContextHolder.requireTenantId();
        List<ProposalVO> proposals = proposalAppQueryService
                .findByConditions(proposalNo, customerId, productCode, status, tenantId, page, size)
                .stream()
                .map(proposalWebMapper::toVO)
                .toList();
        return ApiResponse.success(proposals);
    }

    /**
     * 多条件分页查询投保意向单，返回完整分页元数据。
     */
    @GetMapping("/page")
    public ApiResponse<Page<ProposalVO>> pageProposals(
         @RequestParam(required = false) String proposalNo,
         @RequestParam(required = false) String customerId,
         @RequestParam(required = false) String productCode,
         @RequestParam(required = false) String status,
         @RequestParam(defaultValue = "0") int page,
         @RequestParam(defaultValue = "20") int size) {
        String tenantId = RequestContextHolder.requireTenantId();
        Page<ProposalVO> proposals = proposalAppQueryService
                .findPageByConditions(proposalNo, customerId, productCode, status, tenantId, page, size)
                .map(proposalWebMapper::toVO);
        return ApiResponse.success(proposals);
    }

    /**
     * 提交投保意向单
     *
     * @param proposalId 投保意向单ID
     * @return 响应结果
     */
    @PutMapping("/{proposalId}/submit")
    public ApiResponse<Void> submitProposal(@PathVariable String proposalId) {
        String tenantId = RequestContextHolder.requireTenantId();
        proposalApplicationService.submitProposal(proposalId, "提交投保意向单", tenantId);
        return ApiResponse.success();
    }
}
