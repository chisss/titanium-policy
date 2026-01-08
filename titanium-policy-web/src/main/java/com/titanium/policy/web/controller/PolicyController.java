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
import com.titanium.policy.application.PolicyApplicationService;
import com.titanium.policy.command.CreatePolicyCommand;

import jakarta.annotation.Resource;

/**
 * 保单控制器
 */
@RestController
@RequestMapping("/api/policies")
public class PolicyController {

    @Resource
    private PolicyApplicationService policyApplicationService;

    /**
     * 创建保单
     *
     * @param command 创建保单命令
     * @return 响应结果
     */
    @PostMapping
    public ResponseEntity<String> createPolicy(@RequestBody CreatePolicyCommand command) {
        String policyId = policyApplicationService.createPolicy(command);
        return new ResponseEntity<>(policyId, HttpStatus.CREATED);
    }

    /**
     * 获取保单详情
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 保单详情
     */
    @GetMapping("/{policyId}")
    public ResponseEntity<Policy> getPolicy(@PathVariable String policyId,
                                            @RequestHeader("X-Tenant-Id") String tenantId) {
        Optional<Policy> policy = policyApplicationService.getPolicyById(policyId, tenantId);
        return policy.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 激活保单
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 响应结果
     */
    @PutMapping("/{policyId}/activate")
    public ResponseEntity<Void> activatePolicy(@PathVariable String policyId,
                                               @RequestHeader("X-Tenant-Id") String tenantId) {
        policyApplicationService.activatePolicy(policyId, tenantId);
        return ResponseEntity.noContent().build();
    }
}
