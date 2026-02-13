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

import com.titanium.policy.aggregate.Policy;
import com.titanium.policy.application.command.PolicyApplicationService;
import com.titanium.policy.application.query.PolicyAppQueryService;
import com.titanium.policy.command.CancelPolicyCommand;
import com.titanium.policy.command.CreatePolicyCommand;
import com.titanium.policy.command.CreatePolicyDirectlyCommand;
import com.titanium.policy.command.ResumePolicyCommand;
import com.titanium.policy.command.SuspendPolicyCommand;
import com.titanium.policy.command.TerminatePolicyCommand;
import com.titanium.policy.service.IssuanceRequest;
import com.titanium.policy.service.IssuanceResult;
import com.titanium.policy.valueobject.IssuanceProcessConfig;

import jakarta.annotation.Resource;

/**
 * 保单控制器
 */
@RestController
@RequestMapping("/api/policies")
public class PolicyController {

    @Resource
    private PolicyApplicationService policyApplicationService;

    @Resource
    private PolicyAppQueryService    policyAppQueryService;

    /** 创建保单（从投保单创建） */
    @PostMapping
    public ResponseEntity<String> createPolicy(@RequestBody CreatePolicyCommand command) {
        String policyId = policyApplicationService.createPolicy(command);
        return new ResponseEntity<>(policyId, HttpStatus.CREATED);
    }

    /** 一步出单 */
    @PostMapping("/direct")
    public ResponseEntity<String> createPolicyDirectly(@RequestBody CreatePolicyDirectlyCommand command) {
        String policyId = policyApplicationService.createPolicyDirectly(command);
        return new ResponseEntity<>(policyId, HttpStatus.CREATED);
    }

    /** 智能出单 */
    @PostMapping("/issue")
    public ResponseEntity<IssuanceResult> issueByConfig(@RequestBody IssuanceRequest request,
                                                        @RequestHeader("X-Tenant-Id") String tenantId) {
        IssuanceProcessConfig config = IssuanceProcessConfig.oneStep(request.productCode());
        IssuanceResult result = policyApplicationService.issueByConfig(config, request);
        if (!result.success()) {
            return ResponseEntity.badRequest().body(result);
        }
        return new ResponseEntity<>(result, HttpStatus.CREATED);
    }

    /** 获取保单详情 */
    @GetMapping("/{policyNo}")
    public ResponseEntity<Policy> getPolicy(@PathVariable String policyNo,
                                            @RequestHeader("X-Tenant-Id") String tenantId) {
        Optional<Policy> policy = policyAppQueryService.findByPolicyNo(policyNo, tenantId);
        return policy.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** 签发保单 */
    @PutMapping("/{policyId}/issue")
    public ResponseEntity<Void> issuePolicy(@PathVariable String policyId,
                                            @RequestHeader("X-Operator-Id") String operatorId,
                                            @RequestHeader("X-Tenant-Id") String tenantId) {
        policyApplicationService.issuePolicy(policyId, operatorId, tenantId);
        return ResponseEntity.noContent().build();
    }

    /** 激活保单 */
    @PutMapping("/{policyId}/activate")
    public ResponseEntity<Void> activatePolicy(@PathVariable String policyId,
                                               @RequestHeader("X-Tenant-Id") String tenantId) {
        policyApplicationService.activatePolicy(policyId, tenantId);
        return ResponseEntity.noContent().build();
    }

    /** 暂停保单 */
    @PutMapping("/{policyId}/suspend")
    public ResponseEntity<Void> suspendPolicy(@PathVariable String policyId,
                                              @RequestBody SuspendPolicyCommand command) {
        policyApplicationService.suspendPolicy(command);
        return ResponseEntity.noContent().build();
    }

    /** 恢复保单 */
    @PutMapping("/{policyId}/resume")
    public ResponseEntity<Void> resumePolicy(@PathVariable String policyId,
                                             @RequestBody ResumePolicyCommand command) {
        policyApplicationService.resumePolicy(command);
        return ResponseEntity.noContent().build();
    }

    /** 终止保单 */
    @PutMapping("/{policyId}/terminate")
    public ResponseEntity<Void> terminatePolicy(@PathVariable String policyId,
                                                @RequestBody TerminatePolicyCommand command) {
        policyApplicationService.terminatePolicy(command);
        return ResponseEntity.noContent().build();
    }

    /** 取消保单 */
    @PutMapping("/{policyId}/cancel")
    public ResponseEntity<Void> cancelPolicy(@PathVariable String policyId,
                                             @RequestBody CancelPolicyCommand command) {
        policyApplicationService.cancelPolicy(command);
        return ResponseEntity.noContent().build();
    }
}
