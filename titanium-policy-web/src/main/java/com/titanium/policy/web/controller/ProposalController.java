package com.titanium.policy.web.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.policy.application.command.ProposalApplicationService;
import com.titanium.policy.application.query.ProposalAppQueryService;
import com.titanium.policy.command.CreateProposalCommand;
import com.titanium.policy.web.dto.CreateProposalDTO;
import com.titanium.policy.web.mapper.ProposalWebMapper;
import com.titanium.policy.web.response.ProposalVO;

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
     * @param tenantId 租户ID
     * @return 意向单ID
     */
    @PostMapping
    public ResponseEntity<String> createProposal(@RequestBody CreateProposalDTO request,
                                                 @RequestHeader("X-Tenant-Id") String tenantId) {
        // 协议转换：HTTP Request → 领域命令，收敛到同一应用层门面
        CreateProposalCommand command = proposalWebMapper.toCommand(request, tenantId);
        String proposalId = proposalApplicationService.createProposal(command);
        return new ResponseEntity<>(proposalId, HttpStatus.CREATED);
    }

    /**
     * 获取投保意向单详情
     *
     * @param proposalId 投保意向单ID
     * @param tenantId 租户ID
     * @return 投保意向单详情
     */
    @GetMapping("/{proposalId}")
    public ResponseEntity<ProposalVO> getProposal(@PathVariable("proposalId") String proposalId,
                                                  @RequestHeader("X-Tenant-Id") String tenantId) {
        Optional<ProposalVO> vo = proposalAppQueryService.findByProposalNo(proposalId, tenantId)
                .map(proposalWebMapper::toVO);
        return vo.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 多条件分页查询投保意向单列表
     * <p>
     * 面向管理后台组合检索：意向单编号（模糊）/险种编码（精确）/状态任意组合。{@code holderName}
     * 参数暂不支持（意向单读模型仅存 customerId，无投保人姓名快照，避免跨域联查破坏 CQRS 隔离）。
     * </p>
     *
     * @param proposalNo  意向单编号（可空）
     * @param holderName  投保人姓名（当前读模型不支持，保留占位）
     * @param productCode 险种编码（可空）
     * @param status      意向单状态（可空）
     * @param page        页码（从0开始）
     * @param size        每页条数
     * @param tenantId    租户ID
     * @return 意向单列表
     */
    @GetMapping
    public ResponseEntity<List<ProposalVO>> listProposals(
            @RequestParam(value = "proposalNo", required = false) String proposalNo,
            @RequestParam(value = "holderName", required = false) String holderName,
            @RequestParam(value = "productCode", required = false) String productCode,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        List<ProposalVO> proposals = proposalAppQueryService
                .findByConditions(proposalNo, productCode, status, tenantId, page, size)
                .stream()
                .map(proposalWebMapper::toVO)
                .toList();
        return ResponseEntity.ok(proposals);
    }

    /**
     * 提交投保意向单
     *
     * @param proposalId 投保意向单ID
     * @param tenantId 租户ID
     * @return 响应结果
     */
    @PutMapping("/{proposalId}/submit")
    public ResponseEntity<Void> submitProposal(@PathVariable("proposalId") String proposalId,
                                               @RequestHeader("X-Tenant-Id") String tenantId) {
        proposalApplicationService.submitProposal(proposalId, "提交投保意向单", tenantId);
        return ResponseEntity.noContent().build();
    }
}
