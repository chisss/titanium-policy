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

import com.titanium.policy.aggregate.Proposal;
import com.titanium.policy.application.command.ProposalApplicationService;
import com.titanium.policy.application.query.ProposalAppQueryService;
import com.titanium.policy.command.CreateProposalCommand;
import com.titanium.policy.command.SubmitProposalCommand;

import jakarta.annotation.Resource;

/**
 * 投保意向单控制器
 */
@RestController
@RequestMapping("/api/proposals")
public class ProposalController {

    @Resource
    private ProposalApplicationService proposalApplicationService;

    @Resource
    private ProposalAppQueryService    proposalAppQueryService;

    /**
     * 创建投保意向单
     *
     * @param command 创建投保意向单命令
     * @return 响应结果
     */
    @PostMapping
    public ResponseEntity<String> createProposal(@RequestBody CreateProposalCommand command) {
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
    public ResponseEntity<Proposal> getProposal(@PathVariable String proposalId,
                                                @RequestHeader("X-Tenant-Id") String tenantId) {
        Optional<Proposal> proposal = proposalAppQueryService.findByProposalNo(proposalId, tenantId);
        return proposal.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
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
        SubmitProposalCommand command = new SubmitProposalCommand(proposalId, "提交投保意向单", tenantId);
        proposalApplicationService.submitProposal(command);
        return ResponseEntity.noContent().build();
    }
}
