package com.titanium.policy.web.controller;

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
import org.springframework.web.bind.annotation.RestController;

import com.titanium.policy.application.command.ProposalApplicationService;
import com.titanium.policy.application.query.ProposalAppQueryService;
import com.titanium.policy.web.mapper.ProposalWebMapper;
import com.titanium.policy.web.request.CreateProposalRequest;
import com.titanium.policy.web.response.ProposalVO;

import jakarta.annotation.Resource;

/**
 * 投保意向单控制器
 * <p>
 * 表现层仅依赖 Web 层 Request/VO 与应用层 command/query 服务，不直接依赖领域命令/聚合根： 写入口经
 * {@link ProposalApplicationService}（应用层构造命令），读入口经 {@link ProposalAppQueryService}
 * 查询读模型并由 {@link ProposalWebMapper} 转 VO。
 * </p>
 */
@RestController
@RequestMapping("/api/proposals")
public class ProposalController {

    @Resource
    private ProposalApplicationService proposalApplicationService;

    @Resource
    private ProposalAppQueryService    proposalAppQueryService;

    @Resource
    private ProposalWebMapper          proposalWebMapper;

    /**
     * 创建投保意向单
     *
     * @param request 创建请求
     * @param tenantId 租户ID
     * @return 意向单ID
     */
    @PostMapping
    public ResponseEntity<String> createProposal(@RequestBody CreateProposalRequest request,
                                                 @RequestHeader("X-Tenant-Id") String tenantId) {
        String proposalId = proposalApplicationService.createProposal(request.getProposalId(), request.getProposalNo(),
                request.getPolicyForm(), request.getChannel(), request.getCustomerId(), request.getIntendedSumInsured(),
                request.getIntendedPremium(), request.getCurrency(), request.getInsurancePeriodStart(),
                request.getInsurancePeriodEnd(), request.getExpectedProductCode(), tenantId);
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
    public ResponseEntity<ProposalVO> getProposal(@PathVariable String proposalId,
                                                  @RequestHeader("X-Tenant-Id") String tenantId) {
        Optional<ProposalVO> vo = proposalAppQueryService.findByProposalNo(proposalId, tenantId)
                .map(proposalWebMapper::toVO);
        return vo.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 提交投保意向单
     *
     * @param proposalId 投保意向单ID
     * @param tenantId 租户ID
     * @return 响应结果
     */
    @PutMapping("/{proposalId}/submit")
    public ResponseEntity<Void> submitProposal(@PathVariable String proposalId,
                                               @RequestHeader("X-Tenant-Id") String tenantId) {
        proposalApplicationService.submitProposal(proposalId, "提交投保意向单", tenantId);
        return ResponseEntity.noContent().build();
    }
}
